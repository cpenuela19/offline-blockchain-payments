<!-- dc9acf01-ec1f-47dd-aab9-a3ff9412b329 273474e6-5012-4343-bc69-03fd9aa6a9ed -->
# Plan: Implementación EIP-2612 (permit) para Pagos Offline

## Estado Actual Verificado

- Usuarios registrados: 5
- Balance cuenta madre: 0.29 ETH (suficiente)
- Contrato actual: 0x7370b1DaBaEbdF3080091b75103cd1A437a5540e
- Migración: Simple (pocos usuarios)

## FASE 0: Limpieza de Código (30 min)

### Objetivo

Eliminar código de la Opción A (approve desde app) que no se usará.

### Archivos a Eliminar

1. `app/src/main/java/com/g22/offline_blockchain_payments/data/config/BlockchainConfig.kt`
2. `app/src/main/java/com/g22/offline_blockchain_payments/data/blockchain/TokenContractHelper.kt`

### Archivos a Revertir

#### `app/src/main/java/com/g22/offline_blockchain_payments/ui/viewmodel/WalletSetupViewModel.kt`

- Eliminar línea 13: `import com.g22.offline_blockchain_payments.data.blockchain.TokenContractHelper`
- Eliminar línea 36: `object ApprovingMotherAccount : SetupState()`
- Eliminar líneas 100-115: Lógica de approve automático que llama a `TokenContractHelper.approveMotherAccount()`

#### `app/src/main/java/com/g22/offline_blockchain_payments/ui/screens/WalletSetupScreen.kt`

- Eliminar función `LoadingApproveScreen()` (añadida al final del archivo)
- Revertir `LaunchedEffect` para remover manejo de `ApprovingMotherAccount`
- Revertir `when` block para remover caso `setupState is WalletSetupViewModel.SetupState.ApprovingMotherAccount`

### Verificación

```bash
cd app && ./gradlew assembleDebug
```

---

## FASE 1: Crear Contrato ERC20Permit (1 hora)

### Objetivo

Crear contrato AgroPuntosV2 con soporte de EIP-2612 usando OpenZeppelin.

### Archivo a Crear: `backend/contracts/AgroPuntosV2.sol`

```solidity
// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@openzeppelin/contracts/token/ERC20/extensions/ERC20Permit.sol";
import "@openzeppelin/contracts/access/Ownable.sol";

contract AgroPuntosV2 is ERC20Permit, Ownable {
    constructor(address initialOwner) 
        ERC20("AgroPuntos", "AP") 
        ERC20Permit("AgroPuntos")
        Ownable(initialOwner)
    {
        _mint(initialOwner, 10_000_000 * 10**decimals());
    }
    
    function mint(address to, uint256 amount) external onlyOwner {
        _mint(to, amount);
    }
    
    function burn(uint256 amount) external {
        _burn(msg.sender, amount);
    }
}
```

### Herramienta: Remix IDE

1. Abrir https://remix.ethereum.org
2. Crear archivo `AgroPuntosV2.sol` con el código anterior
3. Compilar con Solidity 0.8.20
4. Verificar que no haya errores

---

## FASE 2: Desplegar Contrato en Sepolia (30 min)

### Objetivo

Desplegar el nuevo contrato en Sepolia usando Remix + MetaMask.

### Pasos en Remix

1. En el panel "Deploy & Run Transactions":

   - Environment: "Injected Provider - MetaMask"
   - Account: Conectar con cuenta madre (0xd3D738efE95AEBC39348DBE6dB5789187360a53d)
   - Contract: AgroPuntosV2

2. En el campo `initialOwner`, pegar: `0xd3D738efE95AEBC39348DBE6dB5789187360a53d`
3. Click "Deploy" y confirmar en MetaMask
4. Copiar dirección del contrato desplegado

### Archivo a Actualizar: `backend/.env`

```env
# Agregar nueva variable
CONTRACT_ADDRESS_AP_V2=0xNUEVA_DIRECCION_AQUI

# Mantener la vieja por ahora (para migración)
CONTRACT_ADDRESS_AP=0x7370b1DaBaEbdF3080091b75103cd1A437a5540e
```

### Verificar en Etherscan (Opcional)

1. Buscar la dirección en https://sepolia.etherscan.io
2. Si deseas verificar el código fuente, en Remix:

   - Plugin "Contract Verification"
   - Seguir wizard de verificación

---

## FASE 3: Migrar 5 Usuarios Existentes (30 min)

### Objetivo

Transferir balances de los 5 usuarios del contrato viejo al nuevo.

### Archivo a Crear: `backend/scripts/migrate-users.js`

```javascript
const { ethers } = require('ethers');
const sqlite3 = require('sqlite3').verbose();
require('dotenv').config({ path: '../.env' });

const OLD_CONTRACT = process.env.CONTRACT_ADDRESS_AP;
const NEW_CONTRACT = process.env.CONTRACT_ADDRESS_AP_V2;
const PRIVATE_KEY = process.env.PRIVATE_KEY_CUENTA_MADRE;

const ERC20_ABI = [
  "function balanceOf(address) view returns (uint256)",
  "function mint(address to, uint256 amount)"
];

async function migrate() {
  const db = new sqlite3.Database('../vouchers.db');
  const users = await new Promise((resolve, reject) => {
    db.all('SELECT address FROM users', (err, rows) => {
      if (err) reject(err);
      else resolve(rows);
    });
  });
  
  console.log(`Migrando ${users.length} usuarios...`);
  
  const provider = new ethers.JsonRpcProvider(process.env.RPC_URL_PRIMARY);
  const wallet = new ethers.Wallet(PRIVATE_KEY, provider);
  const oldToken = new ethers.Contract(OLD_CONTRACT, ERC20_ABI, provider);
  const newToken = new ethers.Contract(NEW_CONTRACT, ERC20_ABI, wallet);
  
  for (const user of users) {
    const balance = await oldToken.balanceOf(user.address);
    if (balance > 0) {
      console.log(`Migrando ${user.address}: ${ethers.formatEther(balance)} AP`);
      const tx = await newToken.mint(user.address, balance);
      await tx.wait();
      console.log(`  TX: ${tx.hash}`);
    }
  }
  
  console.log('Migración completada');
  db.close();
}

migrate();
```

### Ejecutar Migración

```bash
cd backend/scripts
node migrate-users.js
```

### Verificar Balances

Verificar en Etherscan que los 5 usuarios tengan sus balances en el nuevo contrato.

---

## FASE 4: Backend - Implementar settle con permit() (4 horas)

### Objetivo

Actualizar backend para usar permit en lugar de approve manual.

### Archivo: `backend/server.js`

#### 4.1: Actualizar ABI (línea 86)

```javascript
const ERC20_PERMIT_ABI = [
  "function transfer(address to, uint256 amount) external returns (bool)",
  "function transferFrom(address from, address to, uint256 amount) external returns (bool)",
  "function balanceOf(address account) external view returns (uint256)",
  "function allowance(address owner, address spender) external view returns (uint256)",
  "function decimals() external view returns (uint8)",
  // NUEVAS: EIP-2612
  "function permit(address owner, address spender, uint256 value, uint256 deadline, uint8 v, bytes32 r, bytes32 s) external",
  "function nonces(address owner) external view returns (uint256)",
  "function DOMAIN_SEPARATOR() external view returns (bytes32)"
];

const tokenContract = new ethers.Contract(CONTRACT_ADDRESS, ERC20_PERMIT_ABI, wallet);
```

#### 4.2: Crear función settleWithPermit (añadir antes de los endpoints)

```javascript
async function settleWithPermit(permitData, signature, seller, amount) {
  try {
    console.log('[SETTLE_PERMIT] Iniciando...');
    
    const now = Math.floor(Date.now() / 1000);
    if (permitData.deadline < now) {
      throw new Error('Permit expirado');
    }
    
    const permitValue = ethers.parseUnits(permitData.value, 18);
    const transferAmount = ethers.parseUnits(amount, 18);
    
    if (permitValue < transferAmount) {
      throw new Error('Permit insuficiente');
    }
    
    console.log('[SETTLE_PERMIT] Ejecutando permit...');
    const permitTx = await tokenContract.permit(
      permitData.owner,
      permitData.spender,
      permitValue,
      permitData.deadline,
      signature.v,
      signature.r,
      signature.s
    );
    await permitTx.wait(CONFIRMATIONS);
    console.log(`[SETTLE_PERMIT] Permit OK: ${permitTx.hash}`);
    
    console.log('[SETTLE_PERMIT] Ejecutando transferFrom...');
    const transferTx = await tokenContract.transferFrom(
      permitData.owner,
      seller,
      transferAmount
    );
    await transferTx.wait(CONFIRMATIONS);
    console.log(`[SETTLE_PERMIT] Transfer OK: ${transferTx.hash}`);
    
    return {
      permitTxHash: permitTx.hash,
      transferTxHash: transferTx.hash
    };
  } catch (error) {
    console.error('[SETTLE_PERMIT] Error:', error);
    throw error;
  }
}
```

#### 4.3: Actualizar endpoint /v1/vouchers/settle

Buscar el endpoint `app.post('/v1/vouchers/settle', ...)` (línea ~973) y modificar:

```javascript
app.post('/v1/vouchers/settle', settleLimiter, async (req, res) => {
  try {
    const {
      offer_id,
      buyer_address,
      seller_address,
      amount_ap,
      buyer_sig,
      seller_sig,
      canonical,
      expiry,
      permit,      // NUEVO
      permit_sig   // NUEVO
    } = req.body;
    
    // Validaciones existentes...
    if (!permit || !permit_sig) {
      return res.status(400).json({
        error_code: 'MISSING_PERMIT',
        message: 'Se requieren datos de permit'
      });
    }
    
    if (!permit_sig.v || !permit_sig.r || !permit_sig.s) {
      return res.status(400).json({
        error_code: 'INVALID_PERMIT_SIGNATURE',
        message: 'Firma de permit inválida'
      });
    }
    
    // Verificar firmas del voucher (código existente)
    const okSeller = verifySignature(canonical, seller_sig, sellerLower);
    const okBuyer = verifySignature(canonical, buyer_sig, buyerLower);
    
    if (!okSeller || !okBuyer) {
      return res.status(422).json({
        error_code: 'INVALID_SIGNATURE',
        message: 'Firmas inválidas'
      });
    }
    
    // NUEVO: Ejecutar con permit
    const result = await settleWithPermit(permit, permit_sig, sellerLower, amount_ap);
    
    // Guardar en DB (código existente)
    db.run(/* ... */);
    
    res.json({
      success: true,
      offer_id,
      permit_tx_hash: result.permitTxHash,
      transfer_tx_hash: result.transferTxHash
    });
    
  } catch (error) {
    console.error('[SETTLE] Error:', error);
    res.status(500).json({
      error_code: 'SETTLE_FAILED',
      message: error.message
    });
  }
});
```

#### 4.4: Actualizar CONTRACT_ADDRESS a V2

En línea 46, cambiar:

```javascript
const CONTRACT_ADDRESS = process.env.CONTRACT_ADDRESS_AP_V2 || process.env.CONTRACT_ADDRESS_AP;
```

---

## FASE 5: App - Implementar Firma de Permit (3 horas)

### Objetivo

Implementar firma de permit EIP-2612 en la app Android.

### 5.1: Crear `PermitSigner.kt`

Archivo: `app/src/main/java/com/g22/offline_blockchain_payments/data/crypto/PermitSigner.kt`

```kotlin
package com.g22.offline_blockchain_payments.data.crypto

import android.util.Log
import org.web3j.crypto.Credentials
import org.web3j.crypto.Sign
import org.web3j.utils.Numeric
import java.math.BigInteger

object PermitSigner {
    private const val TAG = "PermitSigner"
    
    data class PermitData(
        val owner: String,
        val spender: String,
        val value: String,
        val nonce: Long,
        val deadline: Long
    )
    
    data class PermitSignature(
        val v: Int,
        val r: String,
        val s: String
    )
    
    fun signPermit(
        permitData: PermitData,
        privateKey: String,
        contractAddress: String,
        chainId: Long = 11155111
    ): PermitSignature {
        try {
            Log.d(TAG, "Firmando permit...")
            val credentials = Credentials.create(privateKey)
            
            val domainSeparator = buildDomainSeparator(
                "AgroPuntos", "1", chainId, contractAddress
            )
            val structHash = buildPermitStructHash(permitData)
            
            val message = Numeric.hexStringToByteArray(
                "0x1901" + 
                Numeric.cleanHexPrefix(domainSeparator) + 
                Numeric.cleanHexPrefix(structHash)
            )
            
            val signatureData = Sign.signMessage(message, credentials.ecKeyPair, false)
            
            return PermitSignature(
                v = signatureData.v.toInt(),
                r = Numeric.toHexString(signatureData.r),
                s = Numeric.toHexString(signatureData.s)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}", e)
            throw e
        }
    }
    
    private fun buildDomainSeparator(name: String, version: String, chainId: Long, contract: String): String {
        val typeHash = "0x8b73c3c69bb8fe3d512ecc4cf759cc79239f7b179b0ffacaa9a75d522b39400f"
        val nameHash = org.web3j.crypto.Hash.sha3String(name)
        val versionHash = org.web3j.crypto.Hash.sha3String(version)
        val chainIdHex = Numeric.toHexStringWithPrefixZeroPadded(BigInteger.valueOf(chainId), 64)
        val contractHex = Numeric.toHexStringWithPrefixZeroPadded(Numeric.toBigInt(contract), 64)
        
        val encoded = typeHash + 
            Numeric.cleanHexPrefix(nameHash) + 
            Numeric.cleanHexPrefix(versionHash) + 
            Numeric.cleanHexPrefix(chainIdHex) + 
            Numeric.cleanHexPrefix(contractHex)
        
        return org.web3j.crypto.Hash.sha3(encoded)
    }
    
    private fun buildPermitStructHash(data: PermitData): String {
        val typeHash = "0x6e71edae12b1b97f4d1f60370fef10105fa2faae0126114a169c64845d6126c9"
        val ownerHex = Numeric.toHexStringWithPrefixZeroPadded(Numeric.toBigInt(data.owner), 64)
        val spenderHex = Numeric.toHexStringWithPrefixZeroPadded(Numeric.toBigInt(data.spender), 64)
        val valueHex = Numeric.toHexStringWithPrefixZeroPadded(BigInteger(data.value), 64)
        val nonceHex = Numeric.toHexStringWithPrefixZeroPadded(BigInteger.valueOf(data.nonce), 64)
        val deadlineHex = Numeric.toHexStringWithPrefixZeroPadded(BigInteger.valueOf(data.deadline), 64)
        
        val encoded = typeHash + 
            Numeric.cleanHexPrefix(ownerHex) + 
            Numeric.cleanHexPrefix(spenderHex) + 
            Numeric.cleanHexPrefix(valueHex) + 
            Numeric.cleanHexPrefix(nonceHex) + 
            Numeric.cleanHexPrefix(deadlineHex)
        
        return org.web3j.crypto.Hash.sha3(encoded)
    }
}
```

### 5.2: Crear archivo de constantes

Archivo: `app/src/main/java/com/g22/offline_blockchain_payments/data/config/BlockchainConstants.kt`

```kotlin
package com.g22.offline_blockchain_payments.data.config

object BlockchainConstants {
    const val CONTRACT_ADDRESS = "0xNUEVA_DIRECCION_V2"  // Actualizar después del deployment
    const val MOTHER_ACCOUNT_ADDRESS = "0xd3D738efE95AEBC39348DBE6dB5789187360a53d"
    const val CHAIN_ID = 11155111L  // Sepolia
}
```

### 5.3: Actualizar `VoucherRepository.kt`

Buscar función `createSettledVoucherWithAddresses` (línea ~152) y agregar campos de permit:

```kotlin
suspend fun createSettledVoucherWithAddresses(
    offerId: String,
    buyerAddress: String,
    sellerAddress: String,
    amount: Long,
    buyerSig: String,
    sellerSig: String,
    canonical: String,
    expiry: Long,
    permitOwner: String,       // NUEVO
    permitSpender: String,     // NUEVO
    permitValue: String,       // NUEVO
    permitNonce: Long,         // NUEVO
    permitDeadline: Long,      // NUEVO
    permitV: Int,              // NUEVO
    permitR: String,           // NUEVO
    permitS: String            // NUEVO
): Result<String> = withContext(Dispatchers.IO) {
    try {
        val voucher = VoucherEntity(
            id = offerId,
            role = Role.BUYER,
            amountAp = amount,
            counterparty = "Vendedor",
            createdAt = System.currentTimeMillis(),
            status = VoucherStatus.GUARDADO_SIN_SENAL,
            asset = "AP",
            expiry = expiry,
            buyerAddress = buyerAddress,
            sellerAddress = sellerAddress,
            buyerSig = buyerSig,
            sellerSig = sellerSig,
            permitOwner = permitOwner,         // NUEVO
            permitSpender = permitSpender,     // NUEVO
            permitValue = permitValue,         // NUEVO
            permitNonce = permitNonce,         // NUEVO
            permitDeadline = permitDeadline,   // NUEVO
            permitV = permitV,                 // NUEVO
            permitR = permitR,                 // NUEVO
            permitS = permitS                  // NUEVO
        )
        voucherDao.insertVoucher(voucher)
        Log.d(TAG, "Voucher guardado con permit: $offerId")
        Result.success(offerId)
    } catch (e: Exception) {
        Log.e(TAG, "Error: ${e.message}", e)
        Result.failure(e)
    }
}
```

### 5.4: Actualizar `VoucherEntity.kt`

Archivo: `app/src/main/java/com/g22/offline_blockchain_payments/data/database/VoucherEntity.kt`

Agregar campos de permit (línea ~30):

```kotlin
@Entity(tableName = "vouchers")
@TypeConverters(VoucherTypeConverters::class)
data class VoucherEntity(
    // ... campos existentes ...
    val buyerSig: String? = null,
    val sellerSig: String? = null,
    // NUEVOS campos de permit
    val permitOwner: String? = null,
    val permitSpender: String? = null,
    val permitValue: String? = null,
    val permitNonce: Long? = null,
    val permitDeadline: Long? = null,
    val permitV: Int? = null,
    val permitR: String? = null,
    val permitS: String? = null
)
```

### 5.5: Actualizar versión de DB

Archivo: `app/src/main/java/com/g22/offline_blockchain_payments/data/database/AppDatabase.kt`

Cambiar versión (línea ~15):

```kotlin
@Database(
    entities = [VoucherEntity::class],
    version = 3,  // Incrementar de 2 a 3
    exportSchema = false
)
```

Agregar migración (después de la clase):

```kotlin
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            ALTER TABLE vouchers ADD COLUMN permitOwner TEXT
        """)
        database.execSQL("""
            ALTER TABLE vouchers ADD COLUMN permitSpender TEXT
        """)
        database.execSQL("""
            ALTER TABLE vouchers ADD COLUMN permitValue TEXT
        """)
        database.execSQL("""
            ALTER TABLE vouchers ADD COLUMN permitNonce INTEGER
        """)
        database.execSQL("""
            ALTER TABLE vouchers ADD COLUMN permitDeadline INTEGER
        """)
        database.execSQL("""
            ALTER TABLE vouchers ADD COLUMN permitV INTEGER
        """)
        database.execSQL("""
            ALTER TABLE vouchers ADD COLUMN permitR TEXT
        """)
        database.execSQL("""
            ALTER TABLE vouchers ADD COLUMN permitS TEXT
        """)
    }
}
```

Y actualizar builder:

```kotlin
Room.databaseBuilder(context, AppDatabase::class.java, "agropuntos.db")
    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)  // Agregar nueva migración
    .build()
```

### 5.6: Actualizar `PaymentBleViewModel.kt`

Buscar función `sendPaymentConfirmation` (línea ~322) y agregar generación de permit:

```kotlin
fun sendPaymentConfirmation(...) {
    // ... código existente hasta después de generar buyerSig ...
    
    // NUEVO: Generar permit
    val deadline = (System.currentTimeMillis() / 1000) + (24 * 60 * 60) // 24 horas
    val permitData = PermitSigner.PermitData(
        owner = buyerAddress,
        spender = BlockchainConstants.MOTHER_ACCOUNT_ADDRESS,
        value = (amount.toBigInteger() * BigInteger.TEN.pow(18)).toString(),
        nonce = 0,  // TODO: Obtener nonce real del backend
        deadline = deadline
    )
    
    val permitSig = PermitSigner.signPermit(
        permitData,
        privateKey,
        BlockchainConstants.CONTRACT_ADDRESS,
        BlockchainConstants.CHAIN_ID
    )
    
    Log.d(TAG, "Permit firmado: v=${permitSig.v}")
    
    // ... continuar con el resto del código existente ...
}
```

### 5.7: Actualizar llamada a createSettledVoucherWithAddresses

En la misma función, donde se guarda el voucher completo después de recibir sellerSig (línea ~500+):

```kotlin
voucherRepository.createSettledVoucherWithAddresses(
    offerId = completeTx.transactionId,
    buyerAddress = completeTx.buyerAddress,
    sellerAddress = completeTx.sellerAddress,
    amount = completeTx.amount,
    buyerSig = completeTx.buyerSig!!,
    sellerSig = sellerSig,
    canonical = completeTx.canonical!!,
    expiry = completeTx.expiry!!,
    // NUEVOS parámetros de permit
    permitOwner = permitData.owner,
    permitSpender = permitData.spender,
    permitValue = permitData.value,
    permitNonce = permitData.nonce,
    permitDeadline = permitData.deadline,
    permitV = permitSig.v,
    permitR = permitSig.r,
    permitS = permitSig.s
)
```

---

## FASE 6: Testing Integral (2 horas)

### Objetivo

Probar el flujo completo end-to-end.

### 6.1: Compilar App

```bash
cd app
./gradlew assembleDebug
```

### 6.2: Test Manual - Crear Wallet

1. Desinstalar app
2. Instalar app nueva
3. Crear wallet nuevo
4. Verificar que reciba 1000 AP en el nuevo contrato (Etherscan)

### 6.3: Test Manual - Pago Offline

1. En celular A (comprador): Abrir app, ir a "Enviar"
2. En celular B (vendedor): Crear QR con monto (ej: 50 AP)
3. Celular A escanea QR
4. Confirmar pago
5. Verificar intercambio BLE
6. Verificar voucher guardado con datos de permit

### 6.4: Test Manual - Settle

1. Conectar ambos celulares a WiFi
2. App sincroniza vouchers automáticamente
3. Verificar en logs del backend:

   - "Ejecutando permit..."
   - "Permit OK: 0x..."
   - "Ejecutando transferFrom..."
   - "Transfer OK: 0x..."

4. Verificar en Etherscan ambas transacciones
5. Verificar balances actualizados

### 6.5: Test de Errores

- Crear permit con deadline expirado (modificar código temporalmente)
- Verificar que backend rechace

---

## FASE 7: Documentación (1 hora)

### Objetivo

Documentar la nueva arquitectura y proceso de migración.

### 7.1: Crear MIGRATION_GUIDE.md

Archivo: `backend/MIGRATION_GUIDE.md`

Documentar:

- Por qué se migró de contrato viejo a nuevo
- Qué es EIP-2612 y cómo funciona
- Cómo se migraron los usuarios
- Cómo verificar que funcionó

### 7.2: Actualizar SECURITY.md

Agregar sección sobre EIP-2612:

- Por qué es seguro
- Cómo se evita el problema del approve
- Flujo de firmas

### 7.3: Actualizar README.md del backend

Actualizar sección de variables de entorno:

- CONTRACT_ADDRESS_AP_V2
- Explicar diferencia con el viejo

---

## Checklist Final

Antes de considerar completa la implementación:

- Fase 0: Código limpio (archivos eliminados/revertidos)
- Fase 1: Contrato creado y compilado en Remix
- Fase 2: Contrato desplegado en Sepolia
- Fase 3: 5 usuarios migrados con balances correctos
- Fase 4: Backend actualizado con permit
- Fase 5: App actualizada con firma de permit
- Fase 6: Tests manuales pasando (pago offline + settle)
- Fase 7: Documentación completa

---

## Notas Importantes

1. Backup de DB antes de migrar:
   ```bash
   cp backend/vouchers.db backend/vouchers.db.backup
   ```

2. La dirección del nuevo contrato (CONTRACT_ADDRESS_AP_V2) debe actualizarse en:

   - backend/.env
   - app/.../BlockchainConstants.kt

3. El archivo temporal `backend/check-status.js` puede eliminarse al final.

4. Si algo falla en migración, se puede revertir usando el backup de la DB.

### To-dos

- [ ] Eliminar archivos de Opción A y revertir cambios en ViewModels
- [ ] Crear AgroPuntosV2.sol con EIP-2612 en Remix
- [ ] Desplegar contrato en Sepolia y actualizar .env
- [ ] Migrar 5 usuarios al nuevo contrato
- [ ] Implementar settleWithPermit en backend
- [ ] Implementar PermitSigner y actualizar flujo de pagos
- [ ] Testing end-to-end del flujo completo
- [ ] Documentar migración y nueva arquitectura