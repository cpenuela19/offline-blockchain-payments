# PROYECTO DE GRADO – ISIS3007

**Pagos Blockchain Offline en Zonas Rurales**

Desarrollado por:

**Santiago Andrés Osorio Osorio** – [s.osorioo@uniandes.edu.co](mailto:s.osorioo@uniandes.edu.co)

**Carlos Enrique Peñuela Mejía** – [ce.penuela20@uniandes.edu.co](mailto:ce.penuela20@uniandes.edu.co)

---

## 1. Descripción general

Este proyecto implementa un sistema de pagos basado en tecnología blockchain capaz de operar **completamente offline**, pensado para contextos rurales de Colombia con conectividad intermitente o inexistente.

La solución permite que un **comprador** y un **vendedor**:

* Realicen un pago en total desconexión usando:

  * **Códigos QR** para bootstrap de la sesión.
  * **Bluetooth Low Energy (BLE)** para el intercambio de datos.
* Generen y almacenen **vouchers firmados criptográficamente** (EIP-191) que representan instrucciones de pago.
* Utilicen un modelo de **shadow balance** para reducir el riesgo de doble gasto mientras no hay acceso al ledger.
* Sincronicen eventualmente estos vouchers hacia la blockchain (Sepolia) mediante un **backend Node.js** que ejecuta la liquidación on-chain.

El diseño prioriza:

* Autocustodia de claves (cada usuario controla su wallet).
* Operación offline-first.
* Uso de hardware realista para zonas rurales (smartphones Android con BLE).

---

## 2. Estructura del repositorio

```text
offline-blockchain-payments/
│
├── android-app/                  # Aplicación Android (Kotlin, Jetpack Compose, BLE, Room, WorkManager)
├── backend/                      # Backend Node.js + SQLite + ethers.js + worker de outbox
├── docs/                         # Material de apoyo (diagramas, especificaciones, etc., si aplica)
└── README.md                     # Este archivo
```

---

## 3. Principales componentes de la solución

### 3.1 Aplicación Android

Componentes clave (a nivel conceptual):

* **Pantalla de envío (`SendScreen`)**

  * Escanea el QR del vendedor.
  * Muestra confirmación de pago.
  * Inicia el escaneo BLE y envía la transacción.

* **Pantalla de recepción (`ReceiveScreen`)**

  * El vendedor ingresa monto y concepto.
  * Genera el QR con parámetros de sesión.
  * Escucha conexiones BLE y procesa el mensaje del comprador.

* **Capa BLE (`BleRepository`, `PaymentBleViewModel`)**

  * Publica un servicio GATT con una característica única.
  * Se encarga del escaneo, conexión, envío y recepción de mensajes JSON.
  * Reconstruye mensajes largos aplicando un buffer sobre los fragmentos recibidos.

* **Módulo de vouchers y wallets (`VoucherRepository`, `VoucherCanonicalizer`, `EthereumSigner`)**

  * Construye el objeto `PaymentBase`.
  * Canonicaliza el JSON en un formato estable.
  * Firma el mensaje con EIP-191 (ECDSA secp256k1) usando la clave privada del usuario.
  * Persiste vouchers y elementos del outbox en Room.

* **Sincronización (`SyncWorker`)**

  * Recupera vouchers pendientes de sincronizar.
  * Llama al backend para liquidar los pagos.
  * Registra tiempos de sincronización.

### 3.2 Backend Node.js

Componentes principales:

* **API HTTP (`/v1/vouchers/settle`)**

  * Recibe solicitudes de liquidación de vouchers.
  * Valida campos, verifica firmas y normaliza direcciones.
  * Encola el voucher en una tabla `outbox` para su procesamiento posterior.

* **Lógica de canonicalización y verificación**

  * Replica la misma canonicalización que la app Android.
  * Usa `ethers.hashMessage` + `ethers.recoverAddress` para verificar firmas EIP-191.

* **Worker de outbox**

  * Cada 10 segundos procesa hasta 10 vouchers pendientes.
  * Verifica balance y `allowance` en el contrato ERC-20 (AgroPuntos en Sepolia).
  * Ejecuta `transferFrom` y, si es necesario, `approve`.
  * Actualiza estados (PENDING → PROCESSING → SENT/FAILED) y registra `tx_hash`.

* **Persistencia**

  * Base de datos SQLite para vouchers, outbox y estados de procesamiento.

---

## 4. Requisitos previos

### 4.1 Backend

* Node.js 18+
* npm
* Acceso a nodos RPC de Ethereum Sepolia (ej. proveedores tipo Infura, Alchemy u otros).
* Archivo `.env` con las URLs RPC necesarias (el backend usa un `FallbackProvider` con múltiples endpoints).

> Nota: revisa `backend/server.js` para ver qué variables de entorno espera el proyecto (nombres exactos de las variables RPC, puerto HTTP, etc.).

### 4.2 Aplicación Android

* Android Studio reciente (Flamingo o superior recomendado).
* Dispositivo(s) físico(s) Android con:

  * Bluetooth Low Energy (BLE) habilitado.
  * Cámara funcional (para escanear QR).
* Se recomienda probar con **dos teléfonos físicos** (uno como comprador, otro como vendedor).

---

## 5. Puesta en marcha del backend

1. Abrir una terminal en:

   ```bash
   cd offline-blockchain-payments/backend
   ```

2. Instalar dependencias:

   ```bash
   npm install
   ```

3. Configurar variables de entorno en `.env` (RPCs de Sepolia, claves necesarias, etc.).

4. Iniciar el backend en modo desarrollo:

   ```bash
   npm run dev
   ```

5. Ver en la salida de la terminal el puerto HTTP donde quedó escuchando el servidor (según la configuración del proyecto).

---

## 6. Puesta en marcha de la app Android

1. Abrir Android Studio.

2. Importar el proyecto desde:

   ```text
   offline-blockchain-payments/android-app
   ```

3. Sincronizar Gradle.

4. Conectar uno o dos teléfonos Android físicos mediante USB.

5. Seleccionar el módulo/app correspondiente y ejecutar:

   * `Run > Run 'app'`

6. Asegurarse de conceder los permisos solicitados por la app:

   * Cámara (para lectura de QR).
   * Bluetooth / ubicación según la versión de Android (para BLE).

---

## 7. Flujo completo de prueba del pago offline

A continuación se describe el **flujo real** que implementa el proyecto, desde la perspectiva de **vendedor** y **comprador**.

### 7.1 Rol vendedor – Generación del QR y recepción del pago

1. Abrir la app en el dispositivo del vendedor.
2. Ir a la pantalla de **recibir pago** (`ReceiveScreen`).
3. Ingresar:

   * Monto en AgroPuntos.
   * Concepto o descripción (opcional).
4. Presionar **“Generar QR”**:

   * La app genera un `sessionId` único.
   * Construye un JSON con:

     * `serviceUuid`
     * `sessionId`
     * `amount`
     * `receiverName`
     * `concept` (si aplica)
   * Crea un código QR a partir de este JSON.
   * Inicia un servidor GATT BLE con:

     * Un servicio `SERVICE_UUID`.
     * Una característica `ECHO_CHAR_UUID` con permisos de read/write.
5. Mostrar el QR en pantalla para que el comprador lo escanee.

### 7.2 Rol comprador – Escaneo de QR, conexión BLE y envío de la transacción

1. Abrir la app en el dispositivo del comprador.
2. Ir a la pantalla de **enviar pago** (`SendScreen`).
3. Presionar el botón de escanear para iniciar el lector de QR:

   * La app parsea el contenido del QR y recupera:

     * `serviceUuid`
     * `sessionId`
     * `amount`
     * `receiverName`
     * `concept`
4. La app muestra una pantalla de **confirmación de pago** donde el comprador revisa:

   * Monto.
   * Nombre del vendedor.
   * Concepto.
5. Al confirmar:

   * Se genera un `transactionId` (UUID).
   * Se construye un objeto `PaymentTransaction` con:

     * `transactionId`
     * `amount`
     * `senderName`
     * `receiverName`
     * `concept`
     * `timestamp`## 11. Licencia

(Completar según la licencia elegida para el proyecto: MIT, Apache 2.0, uso académico interno, etc.)
     * `sessionId`
   * Se guarda internamente en el `ViewModel`.
   * Se inicia el escaneo BLE utilizando el `serviceUuid` del vendedor.
6. Cuando se detecta el dispositivo del vendedor:

   * Se establece conexión GATT.
   * Se descubren los servicios.
   * Al recibir el evento “Servicios descubiertos”, la app:

     * Serializa `PaymentTransaction` a JSON.
     * Escribe este JSON en la característica `ECHO_CHAR_UUID`.
   * Android se encarga de fragmentar el mensaje en múltiples paquetes según el MTU.

### 7.3 Rol vendedor – Reconstrucción del mensaje, creación del voucher y almacenamiento

1. El servidor GATT del vendedor recibe los fragmentos en `onCharacteristicWriteRequest`.
2. Cada fragmento se concatena en un buffer interno.
3. Cuando el mensaje completo tiene formato JSON válido (empieza con `{` y termina con `}`):

   * Se interpreta como un `PaymentTransaction`.
   * Se notifica al `PaymentBleViewModel`.
4. La pantalla de recepción detecta la llegada de una transacción válida y dispara un callback de negocio:

   * `onPaymentReceived(amount, transactionId, senderName, ...)`.
5. A partir de esta información, el módulo `VoucherRepository`:

   * Construye un `PaymentBase` con:

     * `asset`
     * `buyer_address`
     * `seller_address`
     * `offer_id` (basado en `transactionId`)
     * `amount_ap`
     * `expiry`
   * Canonicaliza ese objeto en un JSON ordenado.
   * Firma el JSON con EIP-191 usando la clave privada del wallet del usuario.
   * Genera un `SettleRequest` con:

     * Datos del pago.
     * Firmas `buyer_sig` y `seller_sig`.
   * Persiste un `VoucherEntity` en la base de datos local (Room).
   * Inserta una entrada en la tabla `outbox` con:

     * `id = offer_id`
     * `payload = jsonString` (Settle## 11. Licencia

(Completar según la licencia elegida para el proyecto: MIT, Apache 2.0, uso académico interno, etc.)Request)
     * `attempts = 0`
     * `nextAttemptAt = now`.
6. Se programa un trabajo de sincronización inmediata mediante `SyncWorker`.

### 7.4 Sincronización eventual con el backend y liquidación on-chain

Cuando el dispositivo del vendedor tiene conectividad:

1. `SyncWorker` se ejecuta (por disparo puntual o según política periódica).
2. Recupera elementos del outbox cuya `nextAttemptAt <= now`.
3. Para cada elemento:

   * Llama al endpoint del backend:

     ```http
     POST /v1/vouchers/settle
     ```

     con el `SettleRequest` en el body.
4. El backend:

   * Valida campos requeridos y formato de UUID.
   * Canonicaliza los datos de la misma forma que la app.
   * Verifica las firmas EIP-191 usando `ethers.recoverAddress`.
   * Normaliza direcciones a lowercase.
   * Inserta o actualiza el voucher en la tabla `vouchers`.
   * Encola su `offer_id` en la tabla `outbox` del backend para el worker on-chain.
5. El **worker del backend** (ejecutado cada 10 segundos):

   * Selecciona hasta 10 vouchers pendientes o fallidos con backoff.
   * Verifica que:

     * El saldo del emisor sea suficiente.
     * El `allowance` hacia la cuenta del backend sea suficiente; en caso contrario intenta un `approve`.
   * Ejecuta `transferFrom(from, to, amount)` en el contrato ERC-20.
   * Guarda el `tx_hash` en la base de datos.
   * Espera confirmaciones on-chain.
   * Actualiza el estado del voucher a `SUBIDO_OK / CONFIRMED` y marca el outbox como `SENT`.

El resultado es un flujo **offline-first** en el que el pago ocurre entre dispositivos móviles sin red y se refleja en la blockchain cuando el sistema reconecta.

---

## 8. Métricas disponibles

El sistema registra métricas experimentales, entre ellas:

* **Tiempos de pago offline (ms)**
  Medidos desde la confirmación del comprador hasta la recepción del `PaymentTransaction` en el vendedor.
  Ejemplo de valores observados:
  `2108, 1722, 1589, 1699, 1655, 1664 ms`.

* **Tamaño de los vouchers (bytes)**
  Corresponden al tamaño del `SettleRequest` serializado que se envía al backend.
  Valores observados en pruebas:
  ~`1130–1133 bytes`.

* **Tiempos de sincronización (ms)**
  Medidos desde que se dispara el `SyncWorker` hasta que el backend confirma la inserción/cola del voucher.
  Valores registrados:
  `27600 ms`, `73355 ms`.

* **Estadísticas BLE**

  * Intentos de intercambio BLE: 12.
  * Fallos registrados en esas pruebas: 0.

La aplicación permite exportar estas métricas en un JSON con campos como:

```json
{
  "offline_payment_times_ms": [...],
  "voucher_sizes_bytes": [...],
  "sync_times_ms": [...],
  "ble_failures": 0,
  "ble_attempts": 12,
  "total_offline_payments": 6,
  "total_vouchers_measured": 6,
  "total_syncs_measured": 2,
  "export_timestamp": 1765082927606,
  "export_date": "2025-12-06 23:48:47"
}
```

---

## 9. Limitaciones conocidas

Algunos límites reconocidos del sistema:

* **Backend centralizado**

  * Aunque las claves privadas están autocustodiadas en los teléfonos, el backend sigue siendo un componente central que:

    * Orquesta la liquidación on-chain.
    * Administra la cola de vouchers y outbox.
  * No está diseñado como infraestructura altamente distribuida.

* **Capacidad de procesamiento del backend**

  * El worker procesa hasta **10 vouchers cada 10 segundos** (≈ 1 voucher/segundo, ≈ 3600 vouchers/hora).
  * En escenarios con avalanchas grandes de reconexión, la cola puede crecer y la liquidación demorarse varias horas.

* **Ausencia de trusted hardware**

  * No se emplean módulos de hardware seguro para garantizar prevención absoluta de doble gasto.
  * La mitigación se basa en:

    * Shadow balance.
    * Firmas digitales.
    * Políticas de sincronización y límites de valor.

* **Dependencia de nodos RPC de terceros**

  * El backend usa un `FallbackProvider` con varios RPCs, lo que mitiga caídas individuales, pero no elimina completamente la dependencia de la infraestructura de terceros.

---

## 10. Contacto y soporte

Para soporte técnico, dudas de integración o consultas académicas:

* **Santiago Andrés Osorio Osorio** – [s.osorioo@uniandes.edu.co](mailto:s.osorioo@uniandes.edu.co)
* **Carlos Enrique Peñuela Mejía** – [ce.penuela20@uniandes.edu.co](mailto:ce.penuela20@uniandes.edu.co)

---


