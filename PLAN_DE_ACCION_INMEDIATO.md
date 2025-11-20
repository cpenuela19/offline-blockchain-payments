# 🚀 Plan de Acción Inmediato - AgroPuntos

**Objetivo**: Entender qué está mal y qué hacer para preparar el proyecto para un piloto real en 2-3 semanas

---

## 📊 Estado Actual del Proyecto

### **Lo que SÍ funciona** ✅

Tu proyecto tiene implementadas varias funcionalidades importantes:

1. **Sistema de creación de wallets**: Cuando un usuario nuevo abre la app por primera vez, el backend genera una frase de 10 palabras en español (como "casa perro sol luna rio monte flor cielo mar tierra") y una clave privada criptográfica.

2. **Restauración de wallets**: Si un usuario ya tiene una frase de 10 palabras, puede ingresarla en la app para recuperar su wallet en otro dispositivo.

3. **Cifrado de claves privadas**: El backend guarda las claves privadas cifradas usando un algoritmo de seguridad industrial (AES-256-GCM), no en texto plano.

4. **Pagos offline con BLE**: Dos usuarios pueden hacer transacciones offline escaneando un código QR y conectándose por Bluetooth, incluso sin internet.

5. **Sincronización automática**: Cuando vuelve la conexión, las transacciones offline se suben automáticamente al blockchain.

### **Lo que NO funciona o está incompleto** ⚠️

1. **Balance real no se muestra**: Aunque existe la funcionalidad en el backend para consultar cuántos AgroPuntos tiene un usuario, la app no está usando esa información. Muestra números inventados o hardcodeados.

2. **Usuarios nuevos sin fondos**: Cuando alguien crea un wallet nuevo, empieza con 0 AgroPuntos, lo que significa que no puede probar la app ni hacer transacciones.

3. **Historial vacío**: La pantalla de historial de transacciones existe pero no muestra nada, aunque las transacciones sí se están guardando en la base de datos local.

---

## 🔴 PROBLEMAS CRÍTICOS DE SEGURIDAD

### **Problema 1: La clave privada viaja por internet sin protección adicional** 🔴

**Qué está pasando ahora:**

Cuando un usuario crea o restaura su wallet, la app le pide al backend "dame la clave privada". El backend descifra la clave privada y la envía por internet (aunque sea HTTPS) a la app. La app entonces la guarda cifrada localmente.

**Por qué es peligroso:**

- Si alguien intercepta ese momento específico (con un ataque "man-in-the-middle" en una WiFi pública, por ejemplo), puede capturar la clave privada.
- Con la clave privada, esa persona puede robar TODOS los fondos del usuario.
- Es como enviar la contraseña de tu cuenta bancaria por mensaje de texto: técnicamente va cifrado, pero en el momento que llega al teléfono, alguien podría leerlo.

**Qué se debe hacer:**

La solución es que la clave privada **NUNCA viaje por internet**. En lugar de eso:

1. **En la app**: Cuando el usuario tenga su frase de 10 palabras, la app debe usar esas palabras como una "semilla" para generar matemáticamente la clave privada directamente en el teléfono.

2. **En el backend**: El backend debe hacer exactamente el mismo proceso de generación. Cuando crea el wallet, usa la frase de 10 palabras para calcular qué dirección de Ethereum le corresponde, pero NO debe guardar la clave privada cifrada. Solo necesita guardar:
   - El hash (huella digital) de la frase para verificar el login
   - La dirección pública (como 0x1234...)
   - La clave pública

3. **Sincronización**: Ambos (app y backend) deben usar exactamente el mismo método matemático (mismo algoritmo, mismo número de iteraciones, misma "sal") para que ambos lleguen a la misma clave privada desde la misma frase.

**Qué archivos modificar:**

- **Backend**: Necesitas crear una función que tome las 10 palabras y genere la clave privada usando PBKDF2 (un algoritmo de derivación de claves). Luego modificar el endpoint de creación de wallet para que NO guarde la clave privada cifrada, y eliminar el endpoint que envía la clave privada.

- **App**: Necesitas crear una clase nueva que haga exactamente el mismo cálculo PBKDF2 que el backend. Luego modificar el ViewModel de setup de wallet para que, cuando reciba las 10 palabras, genere la clave privada localmente en lugar de pedirla al backend.

**Tiempo estimado**: 3-4 días (incluye testing para verificar que ambos generen la misma clave)

---

### **Problema 2: Existe un endpoint de "debug" que expone claves privadas** 🔴

**Qué está pasando ahora:**

Hay un endpoint en el backend llamado `/wallet/identity-debug` que fue creado para propósitos de desarrollo. Si alguien le envía una frase de 10 palabras, el backend responde con la dirección, clave pública Y clave privada.

**Por qué es peligroso:**

- Este endpoint NO tiene protección especial
- Está disponible para cualquiera que conozca la URL del backend
- Si alguien obtiene la frase de 10 palabras de un usuario (por phishing, por ver sobre el hombro, etc.), puede usar este endpoint para obtener la clave privada inmediatamente
- Con la clave privada, puede robar todos los fondos

**Qué se debe hacer:**

Tienes dos opciones:

**Opción A (Recomendada)**: Eliminar completamente este endpoint del código. Si lo necesitas para desarrollo, coméntalo o elimínalo y solo agrega temporalmente cuando estés desarrollando localmente.

**Opción B**: Modificar el código para que este endpoint solo esté disponible cuando la aplicación se ejecute en modo desarrollo. Agregar una verificación que diga "si estamos en producción, no registrar este endpoint". Esto se hace con una variable de entorno llamada NODE_ENV.

**Qué archivos modificar:**

- **Backend**: En el archivo `server.js`, buscar donde se define `app.post('/wallet/identity-debug', ...)` y eliminarlo o envolverlo en una condición que verifique el entorno.

**Tiempo estimado**: 1 hora

---

### **Problema 3: Los tokens de sesión nunca expiran** 🟡

**Qué está pasando ahora:**

Cuando un usuario hace login (ya sea creando un wallet nuevo o restaurando uno), el backend le da un "token de sesión" (una cadena aleatoria de 44 caracteres). Ese token es como una llave que le permite a la app comunicarse con el backend sin tener que verificar la frase de 10 palabras cada vez.

El problema es que ese token es válido PARA SIEMPRE. Nunca caduca.

**Por qué es problemático:**

- Si alguien roba ese token (por ejemplo, si el usuario instala una app maliciosa que lee datos de otras apps), puede usarlo indefinidamente
- No hay forma de "cerrar sesión" realmente porque el token siempre será válido
- En un escenario de robo, el atacante tiene acceso perpetuo

**Qué se debe hacer:**

Implementar un sistema de expiración de tokens:

1. **En el backend**: Modificar la tabla de usuarios en la base de datos para agregar un campo nuevo que se llame "session_expires_at" (sesión expira en). Cuando generes un nuevo token, calcular una fecha de expiración (por ejemplo, 7 días desde ahora) y guardarla.

2. **Verificación**: Cada vez que la app haga una petición con un token, el backend debe verificar no solo que el token existe, sino que la fecha actual sea menor a la fecha de expiración. Si ya expiró, responder con un error de "sesión expirada".

3. **En la app**: Cuando la app reciba un error de "sesión expirada", debe llevar al usuario de vuelta a la pantalla de login para que ingrese su frase de 10 palabras de nuevo.

**Qué archivos modificar:**

- **Backend**: Modificar la base de datos para agregar el nuevo campo (migración), modificar la función que genera tokens para guardar la fecha de expiración, y crear o modificar un middleware (función intermedia) que verifique la expiración antes de procesar cualquier petición.

- **App**: Modificar el manejo de errores de red para detectar el error de sesión expirada y navegar al usuario a la pantalla de login.

**Tiempo estimado**: 1 día

---

## 🟡 FUNCIONALIDAD FALTANTE ESENCIAL

### **Problema 4: El balance real no se muestra en la app** 🟡

**Qué está pasando ahora:**

El backend tiene un endpoint que funciona perfectamente: le puedes dar una dirección de Ethereum y te responde con cuántos AgroPuntos tiene esa dirección en el blockchain real. Sin embargo, la app no está usando este endpoint.

Actualmente, si ves números en la pantalla de inicio, probablemente sean valores de prueba hardcodeados (escritos directamente en el código) o siempre muestran el mismo número.

**Por qué es problemático:**

- Los usuarios no saben cuántos AgroPuntos tienen realmente
- No pueden verificar si las transacciones se procesaron correctamente
- No tiene sentido hacer una transacción si no ves el balance actualizarse

**Qué se debe hacer:**

1. **Obtener la dirección del usuario**: Cuando la app inicie, debe obtener la dirección de Ethereum del usuario desde el SessionManager (que ya la tiene guardada).

2. **Llamar al endpoint de balance**: Hacer una petición HTTP al endpoint `GET /v1/wallet/balance?address=0x...` pasando la dirección del usuario.

3. **Mostrar el balance**: Cuando recibas la respuesta, actualizar el StateFlow (flujo de estado) que se muestra en la pantalla de inicio.

4. **Actualización periódica**: Configurar un loop (ciclo) que refresque el balance cada 30 segundos automáticamente, para que si hay cambios en el blockchain, el usuario los vea sin tener que reiniciar la app.

5. **Actualización manual**: Agregar un botón de "refrescar" que permita al usuario actualizar manualmente.

6. **Actualización después de transacciones**: Después de hacer una transacción (especialmente después de sincronizar vouchers offline), refrescar automáticamente el balance.

**Qué archivos modificar:**

- **App**: En el `WalletViewModel`, modificar la función `init` para que llame al endpoint de balance al iniciar. Crear una función `refreshBalance()` que se pueda llamar desde cualquier pantalla. En `HomeScreen` o donde muestres el balance, asegurarte de que esté observando el StateFlow correcto.

**Tiempo estimado**: 2 días

---

### **Problema 5: Los usuarios nuevos no tienen fondos para probar** 🟡

**Qué está pasando ahora:**

Cuando alguien crea un wallet nuevo, su balance empieza en 0 AgroPuntos. Para poder hacer transacciones, necesitan que alguien les transfiera tokens primero. Esto es un problema para un piloto porque:

- Los usuarios no pueden probar la app inmediatamente
- Necesitas manualmente enviar tokens a cada usuario nuevo
- Es una fricción enorme en la experiencia de usuario

**Por qué es importante para el piloto:**

En un piloto, quieres que los usuarios puedan probar todas las funcionalidades de inmediato. Si tienen que esperar a que les transfieras fondos manualmente, muchos se van a frustrar y abandonar antes de probar realmente la app.

**Qué se debe hacer:**

Implementar un "faucet" (grifo) automático:

1. **En el backend**: Después de crear un wallet exitosamente (después de guardarlo en la base de datos), pero antes de responder a la app, hacer automáticamente una transferencia de tokens desde la "cuenta madre" hacia la nueva dirección.

2. **Cantidad inicial**: Definir una cantidad razonable para el piloto (por ejemplo, 100 AgroPuntos) que les permita hacer varias transacciones de prueba.

3. **Manejo de errores**: Si la transferencia falla (por ejemplo, porque la cuenta madre se quedó sin fondos o sin gas), NO debe fallar la creación del wallet. Simplemente loguear el error y continuar. El wallet se crea igual, solo que sin fondos iniciales.

4. **Configuración**: Hacer que la cantidad inicial y si el faucet está activado sean variables de entorno en el archivo `.env`, para que puedas desactivarlo fácilmente en producción.

5. **Informar al usuario**: En la respuesta al crear el wallet, incluir información de cuántos tokens iniciales se le enviaron (o si falló el envío).

**Qué archivos modificar:**

- **Backend**: En el endpoint `POST /wallet/create`, después de guardar el usuario en la base de datos, agregar código que llame a `tokenContract.transfer()` para enviar tokens. Agregar las variables de entorno necesarias. Agregar manejo de errores con try-catch para que un fallo en el faucet no rompa la creación del wallet.

- **.env**: Agregar variables como `FAUCET_ENABLED=true` y `FAUCET_AMOUNT=100`.

**Tiempo estimado**: 1 día

---

### **Problema 6: El PIN es demasiado débil** 🟡

**Qué está pasando ahora:**

El usuario configura un PIN de 4 dígitos para proteger su wallet. Un PIN de 4 dígitos solo tiene 10,000 combinaciones posibles (0000 a 9999).

**Por qué es problemático:**

- Un atacante con acceso físico al teléfono puede probar manualmente varias combinaciones
- No hay límite de intentos: alguien puede probar miles de veces sin penalización
- Un script automatizado podría probar todas las combinaciones en minutos
- 4 dígitos es un estándar antiguo; el estándar actual es 6 dígitos (1 millón de combinaciones)

**Qué se debe hacer:**

Implementar dos mejoras:

**Mejora 1: Cambiar a 6 dígitos**

1. **En la app**: Modificar todas las pantallas donde se pide o configura el PIN para que acepten 6 dígitos en lugar de 4.

2. **Validación**: Actualizar las validaciones de formato para verificar que sean exactamente 6 dígitos numéricos.

3. **UI**: Ajustar el diseño de los campos de entrada para que se vea bien con 6 dígitos.

**Mejora 2: Rate limiting (límite de intentos)**

1. **Crear un gestor de intentos**: Necesitas crear un componente nuevo en la app que guarde cuántos intentos fallidos ha habido.

2. **Límite de intentos**: Después de 3 intentos fallidos, bloquear la app temporalmente.

3. **Bloqueo temporal**: Guardar una marca de tiempo que indique "bloqueado hasta". Por ejemplo, si fallan 3 intentos, bloquear por 5 minutos.

4. **Interfaz de bloqueo**: Cuando la app esté bloqueada, mostrar un mensaje que diga "Demasiados intentos fallidos. Intenta de nuevo en X segundos."

5. **Reset al éxito**: Cuando el usuario ingresa el PIN correcto, resetear el contador de intentos fallidos a cero.

6. **Almacenamiento**: Guardar esta información en SharedPreferences para que persista aunque se cierre la app.

**Qué archivos modificar:**

- **App**: Crear una nueva clase `PinAttemptManager` que maneje la lógica de intentos fallidos y bloqueos. Modificar `WalletSetupScreen` y `WalletUnlockScreen` para validar 6 dígitos. Integrar el `PinAttemptManager` en `WalletUnlockViewModel` para verificar intentos antes de validar el PIN.

**Tiempo estimado**: 1-2 días

---

## 🟢 FUNCIONALIDAD PARA COMPLETAR LA UX

### **Problema 7: El historial de transacciones no se muestra** 🟢

**Qué está pasando ahora:**

Las transacciones se están guardando correctamente en la base de datos local (Room Database) de la app. Existe una pantalla de "Historial" en la interfaz. Pero cuando el usuario entra a esa pantalla, no ve nada o ve un mensaje de "vacío".

**Por qué es importante:**

- Los usuarios quieren ver qué transacciones han hecho
- Es importante para auditoría personal: "¿a quién le pagué 50 AgroPuntos la semana pasada?"
- Da confianza ver que las transacciones se están registrando
- Ayuda a detectar problemas: si una transacción no aparece, hay un bug

**Qué se debe hacer:**

1. **Conectar la pantalla con los datos**: El `VoucherViewModel` ya tiene acceso a todos los vouchers guardados. La `HistoryScreen` debe observar esos datos.

2. **Mostrar los vouchers**: Crear tarjetas (cards) que muestren:
   - Si fue enviado o recibido (basado en las direcciones)
   - Cantidad de AgroPuntos
   - Fecha y hora
   - Estado (pendiente, sincronizado, fallido)
   - Alias de la contraparte (comprador o vendedor)

3. **Lista ordenada**: Mostrar las transacciones más recientes primero (orden descendente por fecha).

4. **Estado vacío**: Si no hay transacciones, mostrar un mensaje amigable como "Aún no has hecho transacciones. ¡Prueba hacer un pago offline!"

5. **Detalles al tocar**: Opcionalmente, permitir que al tocar una tarjeta se abra una pantalla de detalles con más información (hash de transacción, direcciones completas, etc.)

**Qué archivos modificar:**

- **App**: Modificar `HistoryScreen` para que observe el `StateFlow` de vouchers del `VoucherViewModel`. Crear un composable `VoucherCard` que muestre bonito cada voucher. Agregar lógica para mostrar el estado vacío. Agregar formato de fechas legible (no timestamps de Unix).

**Tiempo estimado**: 2 días

---

### **Problema 8: El usuario no puede volver a ver su frase de recuperación** 🟢

**Qué está pasando ahora:**

Cuando un usuario crea un wallet nuevo, se le muestra la frase de 10 palabras UNA SOLA VEZ. Si el usuario dice "Ya la guardé" pero en realidad no lo hizo (o la guardó mal), no hay forma de volver a verla.

**Por qué es un problema:**

- Si el usuario pierde su teléfono sin haber guardado bien la frase, pierde sus fondos PARA SIEMPRE
- No hay forma de recuperar la frase
- Es una presión enorme en un momento (justo al crear el wallet) donde el usuario puede estar apurado o no entender bien la importancia

**Contexto técnico importante:**

Actualmente, el backend NO guarda la frase de 10 palabras. Solo guarda el "hash" (huella digital) de la frase, que sirve para verificar si una frase es correcta al hacer login, pero no se puede "revertir" para obtener la frase original. Esto es por diseño de seguridad.

**Qué se puede hacer (opciones):**

**Opción A - Solución sin cambios en backend (recomendada para piloto):**

1. **Advertencia MUY clara**: Modificar la pantalla que muestra la frase de recuperación para ser MUCHO más explícita. Agregar:
   - Un mensaje grande: "ESTA ES LA ÚNICA VEZ QUE VERÁS TU FRASE"
   - Un mensaje de consecuencias: "Sin esta frase, no podremos ayudarte a recuperar tus fondos si pierdes tu teléfono"
   - Checkboxes que el usuario debe marcar:
     * "La escribí en un papel y la guardé en un lugar seguro"
     * "Entiendo que si pierdo mi frase Y mi teléfono, perderé mis fondos"
     * "Entiendo que nadie (ni el equipo de AgroPuntos) puede recuperar mi frase"
   - No permitir continuar hasta que marque los tres checkboxes

2. **Pantalla de confirmación**: Después de que el usuario diga que guardó la frase, pedir que escriba 3 palabras aleatorias de las 10 para verificar que realmente las anotó.

**Opción B - Guardar frase cifrada (más complejo):**

1. **En el backend**: Modificar el código para que además del hash, guarde la frase cifrada. PERO el cifrado no debe ser con la master key general, sino con una clave derivada de alguna contraseña adicional del usuario.

2. **Contraseña de recuperación**: Cuando el usuario cree el wallet, pedirle que configure una "contraseña de recuperación" diferente al PIN. Esta contraseña se usa para cifrar la frase.

3. **Ver frase de nuevo**: Agregar una opción en configuración "Ver frase de recuperación" que requiera:
   - Ingresar el PIN
   - Ingresar la contraseña de recuperación
   - Confirmación biométrica (huella/face)
   - Mostrar la frase solo por 30 segundos y luego ocultarla automáticamente

**Recomendación para el piloto:**

Usa la Opción A (advertencias muy claras). Es más rápida de implementar y suficientemente segura. La Opción B es para producción a largo plazo.

**Qué archivos modificar (Opción A):**

- **App**: Modificar `SeedPhraseDisplayScreen` para agregar los checkboxes y mensajes de advertencia mucho más visibles. Agregar una segunda pantalla de confirmación que pida 3 palabras aleatorias. Modificar el flujo en `WalletSetupViewModel` para incluir este paso de verificación.

**Tiempo estimado**: 1 día (Opción A) o 2-3 días (Opción B)

---

### **Problema 9: Falta testing de las funcionalidades críticas** 🟢

**Qué está pasando ahora:**

No hay tests automatizados que verifiquen que las funcionalidades críticas funcionan correctamente. Todo el testing es manual: tú o alguien del equipo tiene que abrir la app, probar crear un wallet, hacer una transacción, etc.

**Por qué es problemático:**

- Si cambias algo en el código, no sabes si rompiste algo en otra parte
- La función de derivación de claves DEBE ser idéntica en app y backend, pero no hay forma automática de verificarlo
- Al escalar, los bugs se multiplican sin una red de seguridad
- Para un piloto con usuarios reales, necesitas confianza de que lo básico funciona

**Qué tests son críticos:**

**Test 1: Derivación de claves consistente**

Verificar que si usas la misma frase de 10 palabras:
- La app genera la misma clave privada siempre
- El backend genera la misma clave privada siempre  
- Ambos generan la MISMA clave privada entre sí

**Test 2: Dirección correcta desde frase**

Usar una frase de prueba conocida y verificar que la dirección de Ethereum que se genera es exactamente la esperada (comparar con una calculada manualmente).

**Test 3: Normalización de frases**

Verificar que frases con variaciones (mayúsculas, espacios extra, acentos) se normalizan correctamente:
- "casa Perro SOL" → debe ser válida
- "  casa  perro  " → debe ser válida
- "Casa Perro Sol" → debe generar la misma clave que "casa perro sol"

**Test 4: Flujo completo de wallet**

Un test de integración que simule:
- Crear un wallet (mockear el backend)
- Derivar la clave privada
- Cifrarla con el Keystore (mockear el keystore)
- Guardarla
- Desbloquear el wallet
- Verificar que la clave recuperada es la correcta

**Test 5: Cifrado/descifrado de claves**

Verificar que:
- Al cifrar una clave privada y luego descifrarla, recuperas exactamente la misma clave
- Dos cifraciones de la misma clave producen resultados diferentes (por el IV aleatorio)
- No se puede descifrar con una clave incorrecta

**Qué archivos crear:**

- **App**: Crear archivos de test en `app/src/test/java/`:
  - `KeyDerivationTest.kt` para tests de derivación
  - `WalletSetupViewModelTest.kt` para tests de flujo
  - `KeystoreHelperTest.kt` para tests de cifrado (si es posible mockear el keystore)

- **Backend**: Crear archivos de test en `backend/test/`:
  - `keyDerivation.test.js` para tests de derivación
  - `wallet.test.js` para tests del endpoint de creación
  - `session.test.js` para tests de expiración de tokens

**Herramientas necesarias:**

- **App**: JUnit (ya viene con Android), Mockito (para mocks), Coroutines Test (para testing de código asíncrono)
- **Backend**: Jest (framework de testing popular para Node.js) o Mocha

**Tiempo estimado**: 2-3 días para tests críticos

---

### **Problema 10: No hay manejo robusto de errores de red** 🟢

**Qué está pasando ahora:**

Cuando la app hace una petición al backend (crear wallet, obtener balance, etc.) y falla (porque no hay internet, el backend está caído, o es un timeout), simplemente muestra un error genérico y el usuario no puede hacer nada más que intentar de nuevo manualmente.

**Por qué es problemático para el piloto:**

- Los campesinos en áreas rurales tienen internet intermitente
- El backend puede tener problemas temporales
- Una falla temporal en red no debería requerir intervención del usuario
- Perderás usuarios en el proceso de onboarding si falla una petición

**Qué se debe hacer:**

**Implementar reintentos automáticos:**

1. **Lógica de backoff exponencial**: Si una petición falla, intentar automáticamente de nuevo. Pero no inmediatamente, sino con pausas crecientes:
   - Primer reintento: esperar 1 segundo
   - Segundo reintento: esperar 2 segundos
   - Tercer reintento: esperar 4 segundos
   - Después de 3 intentos, mostrar error al usuario

2. **Distinguir tipos de errores**: No todos los errores deben reintentarse:
   - Errores de red (sin internet, timeout) → reintentar
   - Errores 500 del servidor → reintentar
   - Errores 400 (bad request, datos inválidos) → NO reintentar, mostrar error
   - Errores 401 (sesión expirada) → NO reintentar, ir a login

3. **Feedback visual**: Mientras está reintentando, mostrar un indicador de carga que diga "Reintentando... (intento 2 de 3)"

4. **Cache para balance**: Si la petición de balance falla, mostrar el último balance conocido con una nota "Última actualización: hace 5 minutos. Reintentando..."

5. **Cola de operaciones críticas**: Para operaciones muy importantes (como sincronizar un voucher), si falla después de 3 intentos, guardar en una "cola de reintentos" que se procese en background cuando vuelva la conexión.

**Qué archivos modificar:**

- **App**: Crear una función utilitaria `retryWithBackoff` que pueda envolver cualquier llamada de red. Modificar todos los ViewModels que hacen peticiones (WalletSetupViewModel, WalletViewModel, VoucherViewModel) para usar esta función. Actualizar las pantallas para mostrar estados de "reintentando". Modificar el SyncWorker para usar reintentos.

**Tiempo estimado**: 1-2 días

---

## 📅 Cronograma Semana por Semana

### **SEMANA 1: Seguridad Crítica** 🔴

**Objetivo**: Eliminar los 3 bloqueadores de seguridad críticos

**Lunes-Martes: Implementar derivación de claves**

Qué hacer:
- Crear la función de derivación en el backend usando PBKDF2
- Crear la función idéntica en la app
- Modificar el endpoint de creación de wallet para NO guardar la clave privada cifrada
- Eliminar el endpoint que envía la clave privada
- Modificar la app para derivar la clave localmente
- Verificar con varios casos de prueba que ambos generen la misma clave

Resultado esperado: La clave privada nunca viaja por red. Se genera localmente en el teléfono.

**Miércoles: Eliminar endpoint debug**

Qué hacer:
- Buscar el endpoint `identity-debug` en el backend
- Comentarlo completamente o envolverlo en una verificación de entorno
- Configurar la variable NODE_ENV en producción
- Probar que en modo producción ese endpoint no esté disponible

Resultado esperado: No es posible obtener claves privadas con solo una frase.

**Jueves-Viernes: Implementar expiración de tokens**

Qué hacer:
- Modificar la base de datos para agregar el campo de expiración
- Modificar la generación de tokens para calcular y guardar fecha de expiración
- Crear o modificar el middleware que verifica tokens para que valide la expiración
- Modificar la app para manejar el error de sesión expirada
- Probar que después de 7 días un token deja de funcionar

Resultado esperado: Los tokens expiran después de 7 días.

---

### **SEMANA 2: Funcionalidad Esencial** 🟡

**Objetivo**: Hacer que la app sea usable para un piloto

**Lunes-Martes: Integrar balance real**

Qué hacer:
- Modificar el WalletViewModel para llamar al endpoint de balance
- Configurar actualización periódica cada 30 segundos
- Agregar botón de refresh manual en la UI
- Modificar HomeScreen para observar el balance real
- Actualizar el balance después de sincronizar vouchers
- Probar que el balance se actualiza correctamente después de una transacción

Resultado esperado: Los usuarios ven su balance real de AgroPuntos, actualizado.

**Miércoles: Implementar faucet inicial**

Qué hacer:
- Modificar el endpoint de creación de wallet para transferir tokens después de crear
- Agregar las variables de entorno para configurar el faucet
- Implementar manejo de errores para que un fallo no rompa la creación
- Modificar la respuesta para informar cuántos tokens se enviaron
- Probar que los usuarios nuevos reciben 100 AP automáticamente

Resultado esperado: Los wallets nuevos tienen 100 AP para probar inmediatamente.

**Jueves-Viernes: Mejorar seguridad del PIN**

Qué hacer:
- Crear el componente PinAttemptManager que gestiona intentos
- Modificar todas las pantallas de PIN para usar 6 dígitos
- Integrar el rate limiting en el flujo de desbloqueo
- Configurar bloqueo de 5 minutos después de 3 intentos
- Mostrar el mensaje de bloqueo con contador regresivo
- Probar que después de 3 intentos incorrectos se bloquea

Resultado esperado: PIN de 6 dígitos con protección contra fuerza bruta.

---

### **SEMANA 3: Pulido y Testing** 🟢

**Objetivo**: Completar la experiencia de usuario y validar que funciona

**Lunes-Martes: Hacer funcional el historial**

Qué hacer:
- Modificar HistoryScreen para observar los vouchers del ViewModel
- Crear tarjetas visuales para cada transacción
- Implementar la lógica de mostrar "enviado" vs "recibido"
- Formatear las fechas de forma legible
- Agregar el estado de sincronización
- Mostrar estado vacío cuando no hay transacciones
- Probar haciendo varias transacciones offline y verificando que aparezcan

Resultado esperado: Los usuarios ven el historial completo de sus transacciones.

**Miércoles: Mejorar advertencias de frase**

Qué hacer:
- Modificar la pantalla de frase de recuperación para agregar advertencias MUY claras
- Agregar los 3 checkboxes obligatorios
- Crear la pantalla de verificación de palabras aleatorias
- Modificar el flujo para incluir la verificación
- Hacer las advertencias imposibles de ignorar (no permitir continuar sin checkboxes)
- Probar todo el flujo de creación con las nuevas advertencias

Resultado esperado: Es muy difícil que un usuario no guarde bien su frase.

**Jueves-Viernes: Testing y manejo de errores**

Qué hacer:
- Escribir los tests críticos de derivación de claves
- Escribir tests de normalización de frases
- Escribir tests básicos de flujo de wallet
- Implementar la función de reintentos con backoff
- Integrar los reintentos en todos los ViewModels
- Agregar feedback visual de "reintentando"
- Probar con internet intermitente (modo avión on/off)
- Correr todos los tests y verificar que pasen

Resultado esperado: Tests automatizados verificando lo crítico, app robusta ante fallos de red.

---

## ✅ Estado Final Después de 3 Semanas

### **Seguridad**

✅ Las claves privadas se generan localmente, nunca viajan por red  
✅ No hay endpoints que expongan claves privadas  
✅ Los tokens de sesión expiran después de 7 días  
✅ El PIN es de 6 dígitos con protección contra fuerza bruta  

### **Funcionalidad**

✅ Los usuarios ven su balance real de AgroPuntos  
✅ Los wallets nuevos reciben 100 AP automáticamente  
✅ El historial muestra todas las transacciones  
✅ Advertencias muy claras sobre la frase de recuperación  

### **Robustez**

✅ Tests automatizados verifican las funcionalidades críticas  
✅ Manejo robusto de errores de red con reintentos automáticos  
✅ Feedback visual claro en todos los procesos  

### **Calificación**

**Antes**: 7/10  
**Después**: 9/10  

**Estado**: ✅ Listo para piloto con 30-50 usuarios reales

---

## 🎯 Checklist Final Antes del Piloto

### **Seguridad** ✅

- [ ] La clave privada se deriva localmente desde la frase
- [ ] El backend NO guarda claves privadas (solo direcciones y claves públicas)
- [ ] El endpoint identity-debug está eliminado o solo disponible en desarrollo
- [ ] Los session tokens expiran después de 7 días
- [ ] El PIN es de 6 dígitos
- [ ] Hay rate limiting: 3 intentos de PIN → bloqueo 5 minutos
- [ ] La master key del backend está en una variable de entorno, NO en el código
- [ ] El archivo .env está en .gitignore (no se sube al repositorio)

### **Funcionalidad** ✅

- [ ] El balance real se muestra en la pantalla de inicio
- [ ] El balance se actualiza automáticamente cada 30 segundos
- [ ] Hay un botón de refresh manual
- [ ] Los usuarios nuevos reciben 100 AP automáticamente al crear wallet
- [ ] Si el faucet falla, el wallet se crea igual (no rompe el proceso)
- [ ] El historial muestra todas las transacciones
- [ ] El historial distingue entre enviado y recibido
- [ ] Las fechas se muestran en formato legible ("Hace 5 minutos", "Ayer a las 14:30")

### **UX** ✅

- [ ] Hay advertencias MUY claras sobre guardar la frase de recuperación
- [ ] Hay checkboxes obligatorios que el usuario debe marcar
- [ ] Se verifica que el usuario guardó la frase (pide 3 palabras aleatorias)
- [ ] Los mensajes de error son claros y útiles
- [ ] Hay feedback visual durante procesos largos (loading spinners)
- [ ] El estado de "reintentando" se muestra al usuario
- [ ] Si algo falla, se explica QUÉ falló y QUÉ puede hacer el usuario

### **Testing** ✅

- [ ] Hay tests que verifican que la derivación de claves es idéntica en app y backend
- [ ] Hay tests de normalización de frases (mayúsculas, espacios, acentos)
- [ ] Hay tests básicos de flujo de creación de wallet
- [ ] Se probó en al menos 2 dispositivos físicos diferentes
- [ ] Se probó con conexión intermitente (modo avión on/off)
- [ ] Se probó el flujo completo: crear wallet, recibir fondos, hacer transacción offline, sincronizar

### **Infraestructura** ✅

- [ ] El backend está desplegado en un servidor accesible desde internet
- [ ] Las URLs del RPC están configuradas con fallback
- [ ] La cuenta madre tiene suficientes fondos para el faucet (al menos 5000 AP)
- [ ] La cuenta madre tiene suficiente ETH para gas (al menos 0.1 ETH en Sepolia)
- [ ] Hay un proceso de backup de la base de datos (manual o automático)
- [ ] Las variables de entorno están documentadas
- [ ] Hay logs básicos para monitorear errores

### **Documentación** ✅

- [ ] Hay un README actualizado con instrucciones de instalación
- [ ] Está documentado cómo crear un wallet desde la app
- [ ] Está documentado cómo restaurar un wallet
- [ ] Está documentado cómo hacer una transacción offline
- [ ] Hay un proceso claro para reportar bugs durante el piloto
- [ ] Los usuarios del piloto tienen un contacto (WhatsApp, Telegram, email) para soporte

---

## 🚀 Listo para Lanzar el Piloto

### **Criterios de Éxito para el Piloto**

**Métricas de onboarding:**
- 90%+ de usuarios completan la creación de wallet exitosamente
- 80%+ de usuarios dicen que el proceso fue "fácil" o "muy fácil"
- < 10% de usuarios necesitan soporte para crear su wallet

**Métricas de transacciones:**
- 80%+ de transacciones offline se completan exitosamente
- 95%+ de sincronizaciones son exitosas
- Tiempo promedio de sincronización < 30 segundos

**Métricas de seguridad:**
- 0 pérdidas de fondos por bugs de la aplicación
- 0 reportes de claves privadas comprometidas
- 0 reportes de transacciones no autorizadas

**Métricas de UX:**
- < 5% de usuarios reportan problemas con el balance
- < 10% de usuarios reportan confusión con el historial
- 70%+ de usuarios dicen que volverían a usar la app

### **Plan de Contingencia**

Si durante el piloto encuentras:

**Bug crítico que roba fondos:**
- Apagar el backend inmediatamente
- Notificar a todos los usuarios
- No reactivar hasta resolver el bug

**Bug que impide onboarding:**
- Crear wallets manualmente por el backend
- Enviar las frases por un canal seguro (presencial o cifrado)
- Resolver el bug para nuevos usuarios

**Backend caído:**
- Las transacciones offline siguen funcionando
- Usuarios no pueden crear nuevos wallets
- Usuarios no pueden ver balance actualizado
- Sincronización se procesará cuando vuelva

**Problemas de performance:**
- Reducir frecuencia de actualización de balance (cada 60s en lugar de 30s)
- Limitar número de usuarios simultáneos
- Agregar más RPCs de fallback

---

## 📊 Resumen del Plan

### **Tiempo Total: 3 semanas**

- **Semana 1**: Seguridad crítica (derivación de claves, eliminar endpoint debug, tokens con expiración)
- **Semana 2**: Funcionalidad esencial (balance real, faucet, PIN mejorado)
- **Semana 3**: Pulido y testing (historial, advertencias, tests, manejo de errores)

### **Esfuerzo Estimado**

- **Desarrollo**: 15-18 días de trabajo efectivo
- **Testing manual**: 2-3 días adicionales
- **Despliegue y configuración**: 1 día

### **Recursos Necesarios**

- 1 desarrollador full-time (o 2 part-time)
- Acceso al servidor donde se desplegará el backend
- Fondos en la cuenta madre (ETH para gas + tokens AP para faucet)
- 2-3 dispositivos Android para testing

### **Resultado**

Un sistema completo, seguro y funcional para hacer pagos con blockchain de forma offline, listo para ser probado con usuarios reales en condiciones reales.

**¡Éxito! 🎉**
