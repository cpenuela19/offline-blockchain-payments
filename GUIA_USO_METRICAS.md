# 📊 Guía de Uso: Sistema de Métricas

## ¿Qué hace el sistema de métricas?

El sistema captura automáticamente métricas mientras usas la app:
- ⏱️ **Tiempo de pago offline**: Cuánto tarda un pago desde que se confirma hasta que se guarda
- 📦 **Tamaño de vouchers**: Tamaño en bytes de cada voucher firmado
- 🔄 **Tiempo de sincronización**: Cuánto tarda en sincronizar vouchers con el backend
- 📡 **Fallos BLE**: Cuántos intentos de conexión BLE fallaron vs exitosos

## Cómo usar el sistema

### Paso 1: Ejecutar pagos offline reales

1. **Preparar dos dispositivos Android** con la app instalada
2. **Dispositivo 1 (Vendedor)**:
   - Abre la app
   - Ve a "Recibir AgroPuntos"
   - Ingresa un monto (ej: 100 AP)
   - Se generará un código QR

3. **Dispositivo 2 (Comprador)**:
   - Abre la app
   - Ve a "Dar AgroPuntos"
   - Escanea el QR del vendedor
   - Confirma el pago

4. **Repite este proceso 20-50 veces** para tener datos suficientes

### Paso 2: Ejecutar sincronizaciones

1. Después de hacer varios pagos offline, **conecta ambos dispositivos a internet**
2. La app sincronizará automáticamente los vouchers pendientes
3. **Espera a que se complete la sincronización** (puedes verificar en "Mis pagos")
4. Repite este proceso varias veces con diferentes cantidades de vouchers pendientes

### Paso 3: Exportar métricas

1. **Abre la app** en cualquiera de los dispositivos
2. **Abre el menú** (icono de hamburguesa en la esquina superior izquierda)
3. **Ve a "Tus datos"**
4. **Presiona el botón "Exportar Métricas"**
5. Verás un mensaje con la ruta del archivo JSON

### Paso 4: Obtener el archivo JSON

El archivo se guarda en:
```
/storage/emulated/0/Android/data/com.g22.offline_blockchain_payments/files/metrics/metrics_[timestamp].json
```

**Para acceder al archivo:**

**Opción A: Desde Android Studio**
1. Abre Android Studio
2. Ve a `View > Tool Windows > Device File Explorer`
3. Navega a la ruta mencionada arriba
4. Descarga el archivo JSON

**Opción B: Desde el dispositivo**
1. Instala un explorador de archivos (ej: "Files" de Google)
2. Navega a: `Android/data/com.g22.offline_blockchain_payments/files/metrics/`
3. Copia el archivo JSON a otra ubicación (ej: Descargas)
4. Transfiere el archivo a tu computadora

**Opción C: Usando ADB (desde terminal)**
```bash
adb pull /storage/emulated/0/Android/data/com.g22.offline_blockchain_payments/files/metrics/metrics_[timestamp].json ./
```

### Paso 5: Procesar los datos

El archivo JSON tiene este formato:
```json
{
  "offline_payment_times_ms": [1234, 1456, 1321, ...],
  "voucher_sizes_bytes": [512, 523, 498, ...],
  "sync_times_ms": [2340, 2567, 2100, ...],
  "ble_failures": 3,
  "ble_attempts": 47,
  "total_offline_payments": 50,
  "total_vouchers_measured": 50,
  "total_syncs_measured": 10
}
```

**Para calcular los promedios:**

1. **Tiempo promedio de pago offline:**
   - Suma todos los valores en `offline_payment_times_ms`
   - Divide entre `total_offline_payments`
   - Ejemplo: (1234 + 1456 + 1321) / 3 = 1337 ms

2. **Tamaño promedio de voucher:**
   - Suma todos los valores en `voucher_sizes_bytes`
   - Divide entre `total_vouchers_measured`
   - Ejemplo: (512 + 523 + 498) / 3 = 511 bytes

3. **Tiempo promedio de sincronización:**
   - Suma todos los valores en `sync_times_ms`
   - Divide entre `total_syncs_measured`
   - Ejemplo: (2340 + 2567 + 2100) / 3 = 2336 ms

4. **Porcentaje de fallos BLE:**
   - Divide `ble_failures` entre `ble_attempts`
   - Multiplica por 100
   - Ejemplo: (3 / 47) * 100 = 6.38%

## Ejemplo de script Python para procesar

Puedes usar este script para calcular automáticamente:

```python
import json

# Leer el archivo JSON
with open('metrics_1234567890.json', 'r') as f:
    data = json.load(f)

# Calcular promedios
avg_payment_time = sum(data['offline_payment_times_ms']) / len(data['offline_payment_times_ms'])
avg_voucher_size = sum(data['voucher_sizes_bytes']) / len(data['voucher_sizes_bytes'])
avg_sync_time = sum(data['sync_times_ms']) / len(data['sync_times_ms'])
ble_failure_rate = (data['ble_failures'] / data['ble_attempts']) * 100 if data['ble_attempts'] > 0 else 0

print(f"Tiempo promedio de pago offline: {avg_payment_time:.2f} ms")
print(f"Tamaño promedio de voucher: {avg_voucher_size:.2f} bytes")
print(f"Tiempo promedio de sincronización: {avg_sync_time:.2f} ms")
print(f"Porcentaje de fallos BLE: {ble_failure_rate:.2f}%")
```

## Notas importantes

- ✅ Las métricas se capturan **automáticamente** mientras usas la app normalmente
- ✅ No necesitas hacer nada especial, solo usar la app como siempre
- ✅ Puedes exportar las métricas en cualquier momento
- ✅ Cada exportación crea un nuevo archivo con timestamp
- ✅ Las métricas se acumulan hasta que exportes (no se borran automáticamente)

## Solución de problemas

**¿No veo el botón "Exportar Métricas"?**
- Asegúrate de estar en la pantalla "Tus datos" (menú > Tus datos)

**¿El archivo JSON está vacío?**
- Necesitas hacer al menos un pago offline para que haya métricas
- Verifica que hayas completado pagos reales (no solo abrir la app)

**¿No encuentro el archivo?**
- Usa Android Studio Device File Explorer (más fácil)
- O verifica los permisos de almacenamiento de la app

