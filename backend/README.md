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

## Base de Datos

SQLite se crea automáticamente (`vouchers.db`). La tabla `vouchers` almacena:
- `offer_id` (PK)
- `amount_ap`, `buyer_alias`, `seller_alias`
- `tx_hash`, `status`, `onchain_status`
- `created_at`, `updated_at`

