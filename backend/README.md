# Backend AgroPuntos

Backend Node.js para gestionar vouchers y transferencias de tokens ERC-20 AgroPuntos.

## Requisitos

- Node.js 18+
- npm o yarn
- Cuenta en Infura/Alchemy para RPC (o usar RPC público)
- Wallet con fondos en Sepolia para la cuenta madre

## Instalación

```bash
cd backend
npm install
```

## Configuración

1. Copiar `.env.example` a `.env`:
```bash
cp .env.example .env
```

2. Editar `.env` con tus valores:
   - `RPC_URL`: URL del RPC (Sepolia)
   - `PRIVATE_KEY_CUENTA_MADRE`: Clave privada de la cuenta que tiene los tokens
   - `CONTRACT_ADDRESS_AP`: Dirección del contrato ERC-20 AgroPuntos
   - `CHAIN_ID`: 11155111 para Sepolia
   - `PRIV_KEY_A`: Clave privada del buyer (Juan) - **Requerido para approve automático**
   - `PRIV_KEY_B`: Clave privada del seller (María) - Opcional
   - `MOTHER_ADDRESS`: Dirección de la cuenta madre (opcional, se usa la derivada de PRIVATE_KEY_CUENTA_MADRE por defecto)

## Ejecutar

```bash
# Producción
npm start

# Desarrollo (con auto-reload)
npm run dev
```

El servidor estará en `http://localhost:3000`

## Endpoints

### POST /v1/vouchers
Crea un voucher y ejecuta transferencia de tokens.

**Body:**
```json
{
  "offer_id": "uuid-v4",
  "amount_ap": 12000,
  "buyer_alias": "Juan",
  "seller_alias": "Marta",
  "created_at": 1730580000
}
```

**Respuesta 200:**
```json
{
  "offer_id": "uuid-v4",
  "tx_hash": "0x...",
  "status": "SUBIDO_OK"
}
```

### POST /v1/vouchers/settle
Liquida un voucher offline (doble firma buyer/seller) y lo encola para que la “cuenta madre” ejecute la transferencia cuando haya red.

**Body:**
```json
{
  "offer_id": "offline-001",
  "amount_ap": "100",
  "asset": "AP",
  "expiry": 2000000000,
  "seller_address": "0x8846f77a51371269a9e84310cc978154adbf7cf8",
  "seller_sig": "0x...",
  "buyer_address": "0x1111111111111111111111111111111111111111",
  "buyer_sig": "0x..."
}
```

- Firma canónica: JSON ordenado alfabéticamente (ver `canonicalizePaymentBase` en `server.js`).
- Si ya estaba liquidado devuelve `{ "status": "already_settled", "tx_hash": "0x..." }`.
- Si es válido pero pendiente: `{ "status": "queued" }`.

#### Cadena canónica y prueba de firma

Cadena exacta a firmar para el ejemplo anterior:

```
{"amount_ap":"100","asset":"AP","buyer_address":"0x1111111111111111111111111111111111111111","expiry":2000000000,"offer_id":"offline-001","seller_address":"0x8846f77a51371269a9e84310cc978154adbf7cf8"}
```

Script de firma con `ethers` (reemplaza claves y direcciones):

```bash
node - <<'EOF'
import { Wallet, hashMessage } from 'ethers';

const PRIVATE_KEY = process.env.TEST_PK; // export TEST_PK=0x...
const canonical = '{"amount_ap":"100","asset":"AP","buyer_address":"0x1111111111111111111111111111111111111111","expiry":2000000000,"offer_id":"offline-001","seller_address":"0x8846f77a51371269a9e84310cc978154adbf7cf8"}';

const run = async () => {
  const wallet = new Wallet(PRIVATE_KEY);
  const signature = await wallet.signMessage(canonical);
  const recovered = Wallet.recoverAddress(hashMessage(canonical), signature);

  console.log('Signature:', signature);
  console.log('Recovered address:', recovered);
};

run().catch((err) => {
  console.error(err);
  process.exit(1);
});
EOF
```

### GET /v1/tx/{offer_id}
Consulta el estado de una transacción.

**Respuesta 200:**
```json
{
  "offer_id": "uuid-v4",
  "tx_hash": "0x...",
  "onchain_status": "CONFIRMED|PENDING|FAILED"
}
```

### GET /v1/balance/{alias}
Consulta el balance de un alias.

**Respuesta 200:**
```json
{
  "alias": "Juan",
  "balance_ap": 58200
}
```

## Mapeo Alias → Address

Actualmente el mapeo está hardcodeado en `server.js`. En producción, deberías:
- Usar una base de datos para almacenar alias → address
- Implementar autenticación/autorización
- Validar que el alias pertenece al usuario autenticado

## Seguridad

- ⚠️ **NUNCA** exponer `PRIVATE_KEY_CUENTA_MADRE` en código o logs
- Usar variables de entorno para todos los secretos
- Implementar rate limiting (ya incluido)
- Validar y sanitizar todas las entradas
- En producción, usar HTTPS y autenticación

## Límites y reintentos

- Sin límites de monto por voucher o diarios (límites eliminados).
- El worker vuelve a intentar vouchers con estado `FAILED` automáticamente 60 segundos después.

## Approve Automático

El sistema ahora maneja el `approve` automáticamente cuando es necesario:

- **No necesitas ejecutar `setTransaction.js` manualmente** - Todo se maneja internamente en `server.js`
- Cuando un voucher requiere transferencia y el buyer no tiene suficiente `allowance`, el sistema:
  1. Detecta automáticamente la falta de allowance
  2. Busca la clave privada del buyer en el `.env` (PRIV_KEY_A o PRIV_KEY_B)
  3. Ejecuta el `approve` automáticamente
  4. Continúa con la transacción

**Requisitos para approve automático:**
- `PRIV_KEY_A` debe estar en el `.env` (clave privada del buyer)
- El buyer debe tener suficiente balance de ETH para pagar el gas del approve
- El contrato debe estar correctamente configurado

**Nota de seguridad:** En producción, las claves privadas NO deben estar en el `.env`. Deben manejarse mediante un sistema de wallet management seguro o firmas off-chain.

## Base de Datos

SQLite se crea automáticamente (`vouchers.db`). La tabla `vouchers` almacena:
- `offer_id` (PK)
- `amount_ap`, `buyer_alias`, `seller_alias`
- `tx_hash`, `status`, `onchain_status`
- `created_at`, `updated_at`
- `payload_canonical`, `seller_address`, `buyer_address`, `seller_sig`, `buyer_sig`
- `expiry`, `asset`, `amount_ap_str`

La tabla `outbox` (creada automáticamente) mantiene los vouchers pendientes de liquidar (`state`: PENDING | SENT | FAILED).

El worker (`setInterval(processOutboxOnce, 10000)`) procesa hasta 10 vouchers pendientes cada 10s:
- Verifica balance y allowance del buyer
- Si falta allowance, ejecuta `approve` automáticamente (si PRIV_KEY_A está configurado)
- Convierte `amount_ap_str` → `parseUnits`, ejecuta `transferFrom()` y espera `CONFIRMATIONS`
- Actualiza estados en `vouchers` y marca el registro en `outbox`

