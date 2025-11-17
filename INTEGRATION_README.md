# Integración Backend-Frontend: Sistema de Pagos Offline con Firmas Criptográficas

## 📋 Resumen de la Implementación

Se ha integrado el sistema de pagos offline del backend (`/v1/vouchers/settle`) con la aplicación Android, permitiendo que comprador y vendedor firmen vouchers offline usando ECDSA y los envíen al servidor cuando hay conexión.

### ✅ Componentes Implementados

#### Backend
- **`backend/scripts/settle_test_vector.js`**: Script que genera un vector de prueba criptográfico (canonical, hash, firma) para validar que Android y backend hablan el mismo idioma.

#### Android - Infraestructura Criptográfica
- **`app/src/main/java/com/g22/offline_blockchain_payments/data/config/WalletConfig.kt`**: 
  - Claves privadas hardcodeadas para demo (BUYER_PRIVATE_KEY y SELLER_PRIVATE_KEY)
  - Direcciones derivadas de las claves
  - ⚠️ **IMPORTANTE**: Las claves deben coincidir con `PRIV_KEY_A` y `PRIV_KEY_B` del `.env` del backend

- **`app/src/main/java/com/g22/offline_blockchain_payments/data/crypto/VoucherCanonicalizer.kt`**:
  - Función `canonicalizePaymentBase()` que replica exactamente la lógica del backend
  - Orden fijo: `asset`, `buyer_address`, `expiry`, `offer_id`, `seller_address`, `amount_ap`
  - Normalización de direcciones a lowercase

- **`app/src/main/java/com/g22/offline_blockchain_payments/data/crypto/EthereumSigner.kt`**:
  - `signMessageEip191()`: Firma mensajes según estándar EIP-191 (Ethereum Signed Message)
  - `getAddressFromPrivateKey()`: Obtiene dirección desde clave privada
  - `recoverAddress()`: Recupera dirección desde firma (para verificación)

#### Android - API y Repositorio
- **`app/src/main/java/com/g22/offline_blockchain_payments/data/api/SettleRequest.kt`**: Modelo de request para `/v1/vouchers/settle`
- **`app/src/main/java/com/g22/offline_blockchain_payments/data/api/SettleResponse.kt`**: Modelo de response
- **`app/src/main/java/com/g22/offline_blockchain_payments/data/api/VoucherApiService.kt`**: Método `settleVoucher()` agregado
- **`app/src/main/java/com/g22/offline_blockchain_payments/data/repository/VoucherRepository.kt`**: 
  - Método `createSettledVoucherDemo()` que crea, firma y envía un voucher de prueba

#### Android - UI y ViewModel
- **`app/src/main/java/com/g22/offline_blockchain_payments/ui/viewmodel/VoucherViewModel.kt`**: 
  - Método `testSettleVoucher()` para probar desde la UI
  - Estado `settleTestResult` para mostrar resultados
- **`app/src/main/java/com/g22/offline_blockchain_payments/ui/components/DrawerMenu.kt`**: 
  - Botón temporal "🧪 TEST SETTLE" agregado (solo para desarrollo)
- **`app/src/main/java/com/g22/offline_blockchain_payments/MainActivity.kt`**: 
  - Integración del VoucherViewModel y SnackbarHost

#### Dependencias
- **`app/build.gradle.kts`**: Agregada dependencia `org.web3j:crypto:4.9.8` para firmas ECDSA

---

## 🧪 Cómo Probar la Integración

### Prerequisitos

1. **Backend corriendo**: El servidor debe estar ejecutándose en `http://localhost:3000` (o la IP configurada en `ApiClient.kt`)
2. **Claves privadas configuradas**: Las claves en `WalletConfig.kt` deben coincidir con las del `.env` del backend

### Paso 1: Verificar el Vector de Prueba del Backend

Primero, genera el vector de prueba para validar que el backend funciona correctamente:

```bash
cd backend
node scripts/settle_test_vector.js
```

**Salida esperada:**
```json
{
  "canonical": "{\"asset\":\"AP\",\"buyer_address\":\"0x...\",\"expiry\":1893456000,\"offer_id\":\"550e8400-e29b-41d4-a716-446655440000\",\"seller_address\":\"0x...\",\"amount_ap\":\"50\"}",
  "messageHash": "0x...",
  "address": "0x...",
  "signature": "0x...",
  "base": { ... }
}
```

**Guarda este JSON** - lo usarás para comparar con Android.

### Paso 2: Configurar las Claves Privadas en Android

**⚠️ CRÍTICO**: Las claves privadas en `WalletConfig.kt` deben ser **exactamente las mismas** que en el `.env` del backend.

Edita `app/src/main/java/com/g22/offline_blockchain_payments/data/config/WalletConfig.kt`:

```kotlin
const val BUYER_PRIVATE_KEY = "0x..."  // Debe ser EXACTAMENTE PRIV_KEY_A del .env
const val SELLER_PRIVATE_KEY = "0x..." // Debe ser EXACTAMENTE PRIV_KEY_B del .env
```

**Cómo obtener las claves del backend:**
1. Abre el archivo `.env` en la raíz del proyecto (o en `backend/.env`)
2. Copia el valor de `PRIV_KEY_A` → pégalo en `BUYER_PRIVATE_KEY`
3. Copia el valor de `PRIV_KEY_B` → pégalo en `SELLER_PRIVATE_KEY`

**Verificación:**
- Las direcciones se derivan automáticamente de las claves
- `WalletConfig.BUYER_ADDRESS` debe coincidir con `A_ADDRESS` del `.env`
- `WalletConfig.SELLER_ADDRESS` debe coincidir con `B_ADDRESS` del `.env`
- Si no coinciden, las claves privadas están incorrectas

### Paso 3: Iniciar el Backend

```bash
cd backend
npm run dev
```

Verifica que el servidor esté corriendo:
```bash
curl http://localhost:3000/v1/tx/test
```

**Nota sobre la URL del backend:**
- Si usas **emulador Android**: Usa `http://10.0.2.2:3000` en `ApiClient.kt`
- Si usas **dispositivo físico**: 
  1. Encuentra la IP de tu máquina: `hostname -I` (Linux) o `ipconfig` (Windows)
  2. Cambia `BASE_URL` en `ApiClient.kt` a esa IP (ej: `http://192.168.0.15:3000`)
  3. Asegúrate de que el dispositivo y la máquina estén en la misma red WiFi
  4. Verifica que el firewall no esté bloqueando el puerto 3000

### Paso 4: Compilar y Ejecutar la App Android

1. Abre el proyecto en Android Studio
2. Sincroniza Gradle (para descargar la dependencia `web3j:crypto`)
3. Conecta un dispositivo o inicia un emulador
4. Ejecuta la app

### Paso 5: Probar el Endpoint desde la App

1. **Abre el menú lateral** (botón de menú en la esquina superior izquierda)
2. **Busca el botón "🧪 TEST SETTLE"** (al final del menú)
3. **Presiona el botón**
4. **Observa el resultado**:
   - Un Snackbar aparecerá mostrando el resultado
   - Revisa los logs de Android Studio (filtra por "SettleDemo")

**Logs esperados en Android Studio:**
```
D/SettleDemo: Canonical: {"asset":"AP","buyer_address":"0x...","expiry":1893456000,"offer_id":"550e8400-e29b-41d4-a716-446655440000","seller_address":"0x...","amount_ap":"50"}
D/SettleDemo: Buyer sig: 0x...
D/SettleDemo: Seller sig: 0x...
D/SettleDemo: Response code: 200
D/SettleDemo: Response body: SettleResponse(status=queued, tx_hash=null, ...)
D/SettleDemo: ✅ Voucher aceptado: queued
```

**Logs esperados en el backend:**
```
[OUTBOX] Procesando: 550e8400-e29b-41d4-a716-446655440000
💸 Procesando pago: Juan → María
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
   📋 ID: 550e8400...
   💰 Monto: 50 AgroPuntos
   ...
✅ Transacción confirmada en blockchain
```

### Paso 6: Verificar en la Base de Datos

Puedes verificar que el voucher se guardó correctamente:

```bash
cd backend
sqlite3 vouchers.db "SELECT offer_id, amount_ap, status, tx_hash FROM vouchers WHERE offer_id='550e8400-e29b-41d4-a716-446655440000';"
```

---

## 🔍 Troubleshooting

### Error: "422 - Error de validación de firmas"

**Causa**: Las firmas no coinciden con las esperadas por el backend.

**Solución paso a paso**:

1. **Verifica las claves privadas**:
   ```bash
   # En el backend, verifica qué dirección genera PRIV_KEY_A
   node -e "const {ethers} = require('ethers'); console.log(new ethers.Wallet(process.env.PRIV_KEY_A).address)"
   ```
   Compara con `WalletConfig.BUYER_ADDRESS` en Android

2. **Compara el canonical**:
   - Ejecuta `node backend/scripts/settle_test_vector.js` y guarda el `canonical`
   - En Android, revisa los logs cuando presionas "TEST SETTLE"
   - Deben ser **exactamente iguales** (mismo orden, mismo formato JSON, mismo spacing)

3. **Verifica las firmas**:
   - El `signature` del vector de prueba debe coincidir con la firma generada en Android
   - Si no coinciden, el problema está en `EthereumSigner.signMessageEip191()`

4. **Debug detallado**:
   - Agrega logs en `VoucherCanonicalizer.kt` para ver el JSON generado
   - Agrega logs en `EthereumSigner.kt` para ver el hash del mensaje antes de firmar

### Error: "CLEARTEXT communication not permitted by network security policy"

**Causa**: Android bloquea las conexiones HTTP (no HTTPS) por defecto desde Android 9+.

**Solución**: Ya está configurado en `app/src/main/res/xml/network_security_config.xml` y referenciado en `AndroidManifest.xml`. Si aún ves este error:

1. Verifica que `network_security_config.xml` existe en `app/src/main/res/xml/`
2. Verifica que `AndroidManifest.xml` tiene `android:networkSecurityConfig="@xml/network_security_config"`
3. Recompila la app: `./gradlew clean assembleDebug`
4. Reinstala la app en el dispositivo

### Error: "Connection refused" o timeout

**Causa**: El backend no está corriendo, la URL está mal configurada, o hay problemas de red/firewall.

**Solución paso a paso**:

1. **Verifica que el backend esté corriendo**:
   ```bash
   curl http://localhost:3000/v1/tx/test
   ```
   Debe responder con JSON (aunque sea un error, significa que está corriendo).

2. **Si usas emulador Android**:
   - Usa `http://10.0.2.2:3000` en `ApiClient.kt`
   - Esta IP es especial del emulador y apunta al localhost de la máquina

3. **Si usas dispositivo físico**:
   - **IMPORTANTE**: El dispositivo y la máquina deben estar en la misma red WiFi
   - **Desactiva los datos móviles** en el dispositivo para forzar WiFi
   - Encuentra la IP de tu máquina:
     ```bash
     # Linux/Mac
     hostname -I
     # o
     ip addr show | grep "inet " | grep -v "127.0.0.1"
     
     # Windows
     ipconfig
     # Busca "IPv4 Address" en la sección de tu adaptador WiFi
     ```
   - Cambia `BASE_URL` en `ApiClient.kt` a esa IP (ej: `http://192.168.0.15:3000`)
   - Verifica que el servidor esté escuchando en todas las interfaces:
     ```bash
     # Debe mostrar 0.0.0.0:3000, no 127.0.0.1:3000
     netstat -tuln | grep 3000
     # o
     ss -tuln | grep 3000
     ```
   - Si el servidor solo escucha en 127.0.0.1, edita `backend/server.js`:
     ```javascript
     // Cambiar de:
     app.listen(PORT, () => {
     // A:
     app.listen(PORT, '0.0.0.0', () => {
     ```
   - Verifica que el firewall no esté bloqueando el puerto 3000:
     ```bash
     # Linux (si usas ufw)
     sudo ufw allow 3000
     
     # O verifica con
     netstat -tuln | grep 3000
     ```

4. **Verifica conectividad desde el dispositivo**:
   - **Abre un navegador en el dispositivo** (Chrome, Firefox, etc.)
   - Ve a: `http://TU_IP:3000/v1/tx/test`
   - Debe mostrar un JSON (aunque sea un error, significa que hay conectividad)
   - Si no carga, verifica:
     - ¿El dispositivo está en la misma red WiFi que la máquina?
     - ¿Los datos móviles están desactivados?
     - ¿La IP del dispositivo comienza con el mismo prefijo que la IP de la máquina? (ej: ambos en 192.168.0.x)

### Error: "Unresolved reference" en Android Studio

**Causa**: Gradle no ha sincronizado o falta la dependencia.

**Solución**:
1. Sincroniza Gradle: `File > Sync Project with Gradle Files`
2. Verifica que `org.web3j:crypto:4.9.8` esté en `app/build.gradle.kts`
3. Limpia y reconstruye: `Build > Clean Project` y luego `Build > Rebuild Project`

### Las direcciones no coinciden

**Causa**: Las claves privadas no son las correctas.

**Solución**:
1. Verifica que `WalletConfig.BUYER_ADDRESS` coincida con `A_ADDRESS` del `.env`
2. Verifica que `WalletConfig.SELLER_ADDRESS` coincida con `B_ADDRESS` del `.env`
3. Si no coinciden, actualiza las claves privadas en `WalletConfig.kt`

### El canonical no coincide entre Android y Backend

**Causa**: La función de canonicalización no es idéntica.

**Solución**:
1. Compara `VoucherCanonicalizer.kt` con `server.js` (función `canonicalizePaymentBase`)
2. Verifica el orden de campos: `asset`, `buyer_address`, `expiry`, `offer_id`, `seller_address`, `amount_ap`
3. Verifica que las direcciones se conviertan a lowercase
4. Verifica que `amount_ap` sea string, no número

---

## 📝 Notas Importantes

### Seguridad
- ⚠️ **Las claves privadas están hardcodeadas SOLO para la demo académica**
- En producción, deben estar en Android Keystore o en el backend
- Nunca commitees claves privadas reales al repositorio

### Flujo Completo (Futuro)
El flujo completo offline aún no está implementado. Actualmente:
- ✅ Se puede crear y firmar vouchers
- ✅ Se puede enviar al servidor cuando hay conexión
- ⏳ Falta: Intercambio vía BLE/QR entre comprador y vendedor
- ⏳ Falta: Integración con outbox/sync real

### Próximos Pasos
1. Validar que la Fase 3 funciona correctamente (este README)
2. Fase 4: Conectar con outbox/sync real
3. Fase 5: Integrar con BLE/QR y actualizar pantallas

---

## 📚 Referencias

- **EIP-191**: Ethereum Signed Message standard
- **web3j**: Biblioteca Java para Ethereum
- **Backend**: Ver `backend/server.js` para la implementación del servidor
- **Vector de Prueba**: `backend/scripts/settle_test_vector.js`

---

## 🎯 Éxito Mínimo (Definición)

La integración se considera exitosa cuando:
1. ✅ Desde Android puedes llamar a `/v1/vouchers/settle`
2. ✅ El backend acepta la firma (no 422) y responde `status: "queued"`
3. ✅ Se ve en logs/DB que el voucher llegó correctamente
4. ✅ El voucher se procesa en blockchain y obtiene `tx_hash`

Si todos estos puntos se cumplen, la **Fase 3 está completa** y puedes proceder con las Fases 4 y 5.

---

## ⚡ Resumen Rápido de Comandos

### Generar vector de prueba (backend)
```bash
cd backend && node scripts/settle_test_vector.js
```

### Iniciar backend
```bash
cd backend && npm run dev
```

### Verificar que backend está corriendo
```bash
curl http://localhost:3000/v1/tx/test
```

### Verificar voucher en DB
```bash
cd backend && sqlite3 vouchers.db "SELECT offer_id, amount_ap, status, tx_hash FROM vouchers ORDER BY created_at DESC LIMIT 5;"
```

### Ver logs del backend en tiempo real
```bash
cd backend && npm run dev
# Los logs aparecerán en la consola cuando Android envíe un voucher
```

### Ver logs de Android (Android Studio)
1. Abre Android Studio
2. Ve a `View > Tool Windows > Logcat`
3. Filtra por `SettleDemo` o `VoucherRepository`

---

## 📁 Archivos Clave para Revisar

- **Backend**: `backend/server.js` (función `canonicalizePaymentBase` y endpoint `/v1/vouchers/settle`)
- **Android - Config**: `app/src/main/java/.../data/config/WalletConfig.kt`
- **Android - Crypto**: `app/src/main/java/.../data/crypto/VoucherCanonicalizer.kt` y `EthereumSigner.kt`
- **Android - API**: `app/src/main/java/.../data/api/SettleRequest.kt` y `SettleResponse.kt`
- **Android - Repo**: `app/src/main/java/.../data/repository/VoucherRepository.kt` (método `createSettledVoucherDemo`)
- **Android - UI**: `app/src/main/java/.../ui/components/DrawerMenu.kt` (botón TEST SETTLE)
- **Android - Network Security**: `app/src/main/res/xml/network_security_config.xml` (permite HTTP para desarrollo)

