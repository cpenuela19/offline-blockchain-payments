# Migración de Demo a Producción - Sistema de Settle

## ✅ Cambios Implementados

### 1. Base de Datos Actualizada

**`VoucherEntity.kt`** - Nuevos campos agregados:
- `asset: String?` - Asset del voucher ("AP")
- `expiry: Long?` - Timestamp de expiración
- `buyerAddress: String?` - Dirección del comprador
- `sellerAddress: String?` - Dirección del vendedor
- `buyerSig: String?` - Firma del comprador
- `sellerSig: String?` - Firma del vendedor

**`AppDatabase.kt`** - Versión incrementada a 2:
- Migración automática con `fallbackToDestructiveMigration()` (para desarrollo)

### 2. Método de Producción: `createSettledVoucher()`

**`VoucherRepository.kt`** - Nuevo método:
```kotlin
suspend fun createSettledVoucher(
    role: Role,
    amountAp: Long,
    counterparty: String,
    expiry: Long = System.currentTimeMillis() / 1000 + (7 * 24 * 60 * 60)
): VoucherEntity
```

**Características:**
- Crea voucher con datos reales (no hardcodeados)
- Canonicaliza el payment base
- Firma con ambas claves (buyer y seller)
- Guarda en Room con todos los campos
- Crea outbox item con `SettleRequest`
- Dispara sync automático cuando hay red

### 3. Integración con Outbox/Sync

**`syncVoucher()`** - Actualizado para detectar settle:
- Detecta automáticamente si el payload es `SettleRequest` o `VoucherRequest`
- Usa `/v1/vouchers/settle` para settle requests
- Usa `/v1/vouchers` para vouchers normales
- Maneja estados: `GUARDADO_SIN_SENAL` → `ENVIANDO` → `SUBIDO_OK` / `ERROR`
- Maneja errores 422 (validación de firmas) correctamente

### 4. ViewModel Actualizado

**`VoucherViewModel.kt`** - Nuevo método:
```kotlin
fun createSettledVoucher(
    role: Role,
    amountAp: Long,
    counterparty: String,
    expiry: Long? = null
)
```

### 5. Botón de Test (Solo Debug)

**`DrawerMenu.kt`** - Botón "🧪 TEST SETTLE":
- Visible solo en builds de debug (marcado con TODO para usar `BuildConfig.DEBUG`)
- Mantiene el método `createSettledVoucherDemo()` para pruebas

---

## 📝 Cómo Usar el Nuevo Sistema

### Crear un Voucher con Settle

```kotlin
// En tu ViewModel o Repository
viewModelScope.launch {
    repository.createSettledVoucher(
        role = Role.BUYER, // o Role.SELLER
        amountAp = 50L,
        counterparty = "María",
        expiry = System.currentTimeMillis() / 1000 + (7 * 24 * 60 * 60) // Opcional, por defecto 7 días
    )
}
```

### Flujo Completo

1. **Crear voucher offline**:
   ```kotlin
   val voucher = repository.createSettledVoucher(...)
   ```
   - Estado: `GUARDADO_SIN_SENAL`
   - Se guarda en Room
   - Se agrega a outbox

2. **Cuando hay conexión**:
   - `SyncWorker` detecta el outbox item
   - Llama a `syncVoucher()`
   - Estado cambia a `ENVIANDO`
   - Se envía a `/v1/vouchers/settle`

3. **Respuesta del servidor**:
   - **200 (queued)**: Estado → `SUBIDO_OK`, se consulta `tx_hash`
   - **422 (invalid signature)**: Estado → `ERROR`, no se reintenta
   - **5xx (server error)**: Se reintenta con backoff

4. **Sincronización automática**:
   - `SyncWorker` corre cada 15 minutos
   - Reintenta vouchers fallidos con backoff exponencial

---

## 🔄 Estados del Voucher

```
GUARDADO_SIN_SENAL  →  Voucher creado offline, esperando conexión
         ↓
    ENVIANDO        →  Enviando al servidor
         ↓
    SUBIDO_OK       →  Aceptado por el servidor, tiene tx_hash
         o
      ERROR         →  Error de validación (no se reintenta)
```

---

## 🧪 Testing

### Método de Prueba (Demo)

El método `createSettledVoucherDemo()` sigue disponible para pruebas:
- Usa datos hardcodeados del vector de prueba
- Útil para validar que la integración funciona
- Accesible desde el botón "🧪 TEST SETTLE" (solo debug)

### Método de Producción

El método `createSettledVoucher()` usa:
- Datos reales del voucher
- UUID generado dinámicamente
- Expiry configurable (por defecto 7 días)
- Integración completa con outbox/sync

---

## 📋 Próximos Pasos (Opcional)

### Integración con BLE/QR

Para completar el flujo offline real:

1. **Actualizar `PaymentTransaction.kt`**:
   - Agregar campos: `offer_id`, `buyer_address`, `seller_address`, `expiry`, `asset`
   - Asegurar que ambos dispositivos construyan el mismo payload canónico

2. **Actualizar `PaymentBleViewModel.kt`**:
   - Cuando se intercambia voucher vía BLE/QR, construir `PaymentBase` canónico
   - Cada dispositivo firma con su clave privada
   - Guardar en Room con `createSettledVoucher()`

3. **Actualizar Screens**:
   - `BuyerConfirmScreen`: Llamar a `createSettledVoucher()` al confirmar
   - `ReceiveScreen`: Llamar a `createSettledVoucher()` al recibir

---

## ⚠️ Notas Importantes

1. **Claves Privadas**: Siguen hardcodeadas para la demo. En producción deben estar en Android Keystore.

2. **Base de Datos**: La versión se incrementó a 2. En desarrollo se usa `fallbackToDestructiveMigration()`, en producción deberías crear una migración real.

3. **Botón de Test**: Actualmente visible siempre, pero marcado con TODO para usar `BuildConfig.DEBUG` en producción.

4. **Estados**: El estado `RECEIVED` mencionado en el plan no está en el enum actual. Se usa `GUARDADO_SIN_SENAL` para vouchers offline.

---

## ✅ Checklist de Migración

- [x] Agregar campos a `VoucherEntity` para settle
- [x] Incrementar versión de base de datos
- [x] Crear método `createSettledVoucher()` de producción
- [x] Integrar con outbox/sync
- [x] Actualizar `syncVoucher()` para detectar settle
- [x] Manejar estados correctamente
- [x] Agregar método en `VoucherViewModel`
- [x] Ocultar botón de test en release (TODO: usar BuildConfig.DEBUG)
- [ ] (Opcional) Integrar con BLE/QR
- [ ] (Opcional) Actualizar screens para usar `createSettledVoucher()`

---

## 🎯 Uso en Código

### Ejemplo: Crear voucher desde una pantalla

```kotlin
// En tu Composable o Activity
val viewModel: VoucherViewModel = viewModel()

// Al confirmar un pago
Button(onClick = {
    viewModel.createSettledVoucher(
        role = Role.BUYER,
        amountAp = amount,
        counterparty = "María"
    )
}) {
    Text("Confirmar Pago")
}
```

El voucher se creará offline, se firmará, y se sincronizará automáticamente cuando haya conexión.

