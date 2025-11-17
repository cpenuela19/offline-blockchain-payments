# Resumen de Cambios Implementados

## ✅ Cambios Completados

### 1. Manejo de HTTP 409 en syncVoucher()

**Archivo**: `VoucherRepository.kt`

- ✅ Si el servidor devuelve 409, se llama automáticamente a `getTransaction(offer_id)`
- ✅ Si la respuesta contiene `tx_hash`, se actualiza el voucher a:
  - `status = SUBIDO_OK` (si tiene tx_hash)
  - `status = RECEIVED` (si no tiene tx_hash aún)
  - `txHash = valor devuelto`
- ✅ Si falla la consulta, se reintenta después (retorna `false`)

**Implementado en líneas**: 233-255 (settle) y 315-337 (voucher normal)

### 2. Manejo de HTTP 429 en syncVoucher()

**Archivo**: `VoucherRepository.kt`

- ✅ Detecta código 429 (límite de riesgo / rate limit)
- ✅ Guarda error descriptivo: "Límite de riesgo excedido o rate limit"
- ✅ Permite que el Worker reintente con backoff (NO marca ERROR definitivo)
- ✅ Revierte estado a `GUARDADO_SIN_SENAL` para permitir reintento
- ✅ NO elimina de outbox, permitiendo reintento

**Implementado en líneas**: 256-269 (settle) y 338-351 (voucher normal)

### 3. Conversión correcta de amountAp: Long → amount_ap: String

**Archivo**: `VoucherRepository.kt`

- ✅ `SettleRequest(amount_ap = amountAp.toString())` siempre usa String
- ✅ Verificado en `createSettledVoucher()` línea 145
- ✅ Verificado en `createSettledVoucherDemo()` línea 442 (ya es String)

**Nota**: `VoucherRequest` usa `Long` (endpoint antiguo), `SettleRequest` usa `String` (endpoint nuevo). Ambos están correctos.

### 4. Estado RECEIVED agregado

**Archivo**: `VoucherStatus.kt`

- ✅ Agregado `RECEIVED` al enum
- ✅ Se usa cuando la respuesta de `/settle` es "queued" pero antes de obtener `tx_hash`
- ✅ Flujo: `GUARDADO_SIN_SENAL` → `ENVIANDO` → `RECEIVED` → `SUBIDO_OK`

**Implementado en líneas**: 
- Enum: línea 6
- Uso en syncVoucher: líneas 208-212, 244

### 5. TODO en fallbackToDestructiveMigration()

**Archivo**: `AppDatabase.kt`

- ✅ Agregado TODO claro indicando: "TODO: Implementar migración real para producción"
- ✅ No se eliminó nada, solo se agregó el comentario

**Implementado en líneas**: 29-30

### 6. Validación de direcciones derivadas

**Archivo**: `WalletConfig.kt`

- ✅ Agregadas constantes `EXPECTED_BUYER_ADDRESS` y `EXPECTED_SELLER_ADDRESS`
- ✅ Validación en runtime con `if (derivedAddress != EXPECTED_ADDRESS)`
- ✅ Log de advertencia si no coinciden (no rompe la demo)
- ✅ Fallback a dirección esperada si hay error

**Implementado en líneas**: 32-33, 45-50, 66-71

---

## 📋 Flujo de Estados Actualizado

```
GUARDADO_SIN_SENAL  →  Voucher creado offline
         ↓
    ENVIANDO        →  Enviando al servidor
         ↓
    RECEIVED        →  Queued en servidor, esperando tx_hash (nuevo)
         ↓
    SUBIDO_OK       →  Confirmado con tx_hash
         o
      ERROR         →  Error de validación (no se reintenta)
```

**Nota sobre 429**: Cuando hay rate limit, el estado se revierte a `GUARDADO_SIN_SENAL` para permitir reintento.

---

## 🔍 Validaciones Implementadas

### Conversión de amountAp

- ✅ `createSettledVoucher()`: `amountAp.toString()` (línea 145)
- ✅ `createSettledVoucherDemo()`: Ya es String (línea 416, 442)

### Direcciones

- ✅ Validación en `WalletConfig.BUYER_ADDRESS` con log de advertencia
- ✅ Validación en `WalletConfig.SELLER_ADDRESS` con log de advertencia
- ✅ No rompe la demo si no coinciden, solo advierte

### Estados HTTP

- ✅ 200: Maneja "queued" y "already_settled", consulta tx_hash
- ✅ 409: Consulta estado automáticamente, reintenta si falla
- ✅ 422: Error de validación, marca ERROR definitivo
- ✅ 429: Rate limit, permite reintento con backoff
- ✅ 5xx: Reintenta con backoff

---

## ⚠️ Notas

1. **Estado RECEIVED**: Se usa cuando el voucher está "queued" pero aún no tiene `tx_hash`. El Worker seguirá consultando hasta obtener el `tx_hash` y actualizar a `SUBIDO_OK`.

2. **HTTP 429**: No marca ERROR definitivo, permite reintento. El estado se revierte a `GUARDADO_SIN_SENAL` para que el Worker lo reintente.

3. **Validación de direcciones**: Solo advierte en logs, no rompe la demo. Útil para detectar si las claves privadas están incorrectas.

4. **Migración de DB**: El TODO está agregado. En producción, crear una `Migration` de versión 1 a 2 que preserve los datos.

---

## ✅ Checklist

- [x] Manejo de HTTP 409 mejorado
- [x] Manejo de HTTP 429 agregado
- [x] Conversión amountAp verificada
- [x] Estado RECEIVED agregado
- [x] TODO en fallbackToDestructiveMigration
- [x] Validación de direcciones implementada

Todos los cambios solicitados han sido implementados.

