# 📊 Análisis Profundo del Estado Actual - Sistema de Pagos Blockchain Offline

**Fecha**: 20 de noviembre de 2025  
**Proyecto**: AgroPuntos - Offline Blockchain Payments

---

## 🎯 Resumen Ejecutivo

El proyecto ha **avanzado significativamente** desde el análisis anterior. Se implementó un **sistema completo de gestión de wallets con generación en backend**, frases de recuperación en español, cifrado AES-256-GCM, y un flujo de onboarding robusto.

### **Cambios Principales Implementados:**

✅ **Sistema de wallets generadas en backend** (no en app)  
✅ **Frases de recuperación de 10 palabras en español** (sin tildes/ñ)  
✅ **Cifrado AES-256-GCM** de claves privadas en backend  
✅ **Sistema de sesiones** con tokens  
✅ **Flujo completo de onboarding** (crear/restaurar wallet)  
✅ **Android Keystore** para cifrado local de claves  
✅ **PIN de 4 dígitos** para protección  
✅ **Pantalla de datos de usuario** (debug/dev tool)  

---

## 🏗️ Arquitectura Actualizada

### **1. App Móvil (Android/Kotlin)**

#### **🆕 Nuevos Componentes**

##### **A. Sistema de Wallet (`data/wallet/`)**

```kotlin
WalletManager (object)
├─ importPrivateKeyFromBackend() // Importa y cifra clave desde backend
├─ unlockWallet() // Descifra con Android Keystore
├─ getUnlockedPrivateKey() // Obtiene clave de memoria
├─ clearUnlockedWallet() // Limpia memoria
├─ walletExists() // Verifica si existe wallet
└─ isWalletUnlocked() // Verifica si está desbloqueado

KeystoreHelper (object)
├─ encrypt() // Cifra con Android Keystore (AES-256-GCM)
├─ decrypt() // Descifra (requiere auth biométrica opcional)
├─ keyExists() // Verifica si existe clave en Keystore
└─ deleteKey() // Elimina clave

SessionManager (object)
├─ saveSession() // Guarda address, publicKey, sessionToken
├─ getAddress() // Obtiene dirección del usuario
├─ getSessionToken() // Obtiene token de sesión
├─ hasSession() // Verifica sesión activa
└─ clearSession() // Limpia sesión (logout)

SeedPhraseGenerator // No se usa - deprecated
```

**⚠️ Nota Importante**: La generación de wallet se hace en el **BACKEND**, no en la app.

##### **B. Nuevas Pantallas de UI**

```kotlin
WalletSetupScreen
├─ WelcomeScreen // Crear nuevo / Ya tengo wallet
├─ SeedPhraseDisplayScreen // Mostrar frase de 10 palabras
├─ RestorePhraseInputScreen // Ingresar frase para restaurar
└─ PinSetupScreen // Configurar PIN de 4 dígitos

WalletUnlockScreen // Desbloquear wallet con PIN

UserDataScreen // Ver identidad (dev tool)
├─ Ingresa frase de 10 palabras
├─ Llama a /wallet/identity-debug
└─ Muestra: address, public_key, private_key
```

##### **C. Nuevos ViewModels**

```kotlin
WalletSetupViewModel
├─ createWallet() // POST /wallet/create
├─ restoreWallet(phrase10) // POST /auth/login-via-phrase
├─ confirmSeedPhrase()
├─ setPin(pin, confirmPin)
└─ completeSetup() // Cifra y guarda clave con Keystore

WalletUnlockViewModel
├─ unlockWallet(pin) // Descifra clave
└─ States: Initial, Unlocking, Unlocked, Error

UserDataViewModel
└─ verifyIdentity(phrase10) // POST /wallet/identity-debug
```

##### **D. Nuevos Endpoints de API**

```kotlin
interface VoucherApiService {
    @POST("/wallet/create")
    suspend fun createWallet(
        @Body request: CreateWalletRequest
    ): Response<CreateWalletResponse>
    // Response: phrase10, address, public_key, session_token
    
    @POST("/auth/login-via-phrase")
    suspend fun loginViaPhrase(
        @Body request: LoginViaPhraseRequest
    ): Response<LoginViaPhraseResponse>
    // Response: address, public_key, session_token
    
    @GET("/wallet/private-key")
    suspend fun getPrivateKey(
        @Header("X-Session-Token") sessionToken: String
    ): Response<PrivateKeyResponse>
    // Response: private_key (hex)
    
    @POST("/wallet/identity-debug")
    suspend fun identityDebug(
        @Body request: IdentityDebugRequest
    ): Response<IdentityDebugResponse>
    // Response: address, public_key, private_key
    
    @GET("/v1/wallet/balance")
    suspend fun getWalletBalance(
        @Query("address") address: String
    ): Response<WalletBalanceResponse>
    // Response: balance_ap
}
```

##### **E. Nueva Entidad de Base de Datos**

```kotlin
@Entity(tableName = "pending_vouchers")
data class PendingVoucherEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: String,  // "incoming" | "outgoing"
    val amountAp: Long,
    val timestamp: Long,
    val synced: Boolean = false
)
```

**Propósito**: Calcular "shadow balance" (balance pendiente) cuando la app está offline.

##### **F. Flujo de Navegación Actualizado**

```
MainActivity
├─ Si NO hay wallet → WalletSetupScreen
├─ Si hay wallet pero no desbloqueado → WalletUnlockScreen
└─ Si wallet desbloqueado → InitialChoiceScreen (app principal)

WalletSetupScreen
├─ "Crear Wallet"
│   ├─ POST /wallet/create → recibe phrase10
│   ├─ Mostrar SeedPhraseDisplayScreen
│   ├─ Usuario confirma que guardó la frase
│   ├─ PinSetupScreen → configurar PIN
│   ├─ Cifrar clave con Android Keystore
│   └─ Completado → navegar a app principal
│
└─ "Ya tengo wallet"
    ├─ RestorePhraseInputScreen → ingresar 10 palabras
    ├─ POST /auth/login-via-phrase
    ├─ GET /wallet/private-key (con session_token)
    ├─ PinSetupScreen → configurar PIN
    ├─ Cifrar clave con Android Keystore
    └─ Completado → navegar a app principal
```

---

### **2. Backend (Node.js/Express)**

#### **🆕 Nuevos Componentes**

##### **A. Utilidades Crypto (`backend/crypto/`)**

```javascript
// crypto/aes.js
const ALGORITHM = 'aes-256-gcm';
const MASTER_KEY = process.env.WALLET_MASTER_KEY; // 32 bytes (256 bits)

function encryptPrivateKey(plainHex) {
  // Cifra clave privada con AES-256-GCM
  // IV (16 bytes) + authTag (16 bytes) + encrypted
  // Retorna: base64
}

function decryptPrivateKey(cipherText) {
  // Descifra clave privada
  // Retorna: hex con prefijo 0x
}
```

**⚠️ Seguridad**: Requiere `WALLET_MASTER_KEY` en `.env` (32 bytes).

##### **B. Generación de Frases (`backend/utils/phraseGenerator.js`)**

```javascript
// Lista de 2048 palabras en español (sin tildes, sin ñ)
const SPANISH_WORDLIST = [
  'abajo', 'abrir', 'acero', 'acto', ... // 2048 palabras
];

function generatePhrase10() {
  // Genera 10 palabras aleatorias
  // Retorna: ["palabra1", "palabra2", ..., "palabra10"]
}

function normalizePhrase(phrase10) {
  // Normaliza: minúsculas, sin tildes, sin ñ
  // Retorna: "palabra1 palabra2 ... palabra10"
}

function hashPhrase(phrase10) {
  // SHA-256 de la frase normalizada
  // Retorna: hash hex (64 caracteres)
}
```

**Características**:
- 2048 palabras → 11 bits de entropía por palabra
- 10 palabras → 110 bits de entropía (~10^33 combinaciones)
- ⚠️ **NO es BIP39** (BIP39 usa 12/24 palabras con checksum)

##### **C. Sistema de Sesiones (`backend/utils/sessionToken.js`)**

```javascript
function generateSessionToken() {
  // 32 bytes aleatorios en base64
  // Retorna: 44 caracteres base64
}
```

##### **D. Base de Datos Actualizada**

```sql
-- Nueva tabla: users
CREATE TABLE users (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  phrase10_hash TEXT UNIQUE NOT NULL,     -- SHA-256 de la frase
  encrypted_private_key TEXT NOT NULL,    -- Clave cifrada con AES-256-GCM
  public_key TEXT NOT NULL,               -- Clave pública (0x04...)
  address TEXT UNIQUE NOT NULL,           -- Dirección Ethereum (0x...)
  session_token TEXT UNIQUE,              -- Token de sesión (base64)
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);

CREATE INDEX idx_phrase10_hash ON users(phrase10_hash);
CREATE INDEX idx_session_token ON users(session_token);
CREATE INDEX idx_address ON users(address);

-- Tabla existente: vouchers
CREATE TABLE vouchers (
  offer_id TEXT PRIMARY KEY,
  amount_ap INTEGER NOT NULL,
  buyer_alias TEXT NOT NULL,
  seller_alias TEXT NOT NULL,
  tx_hash TEXT,
  status TEXT NOT NULL,
  onchain_status TEXT,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  -- Campos para settle (offline):
  payload_canonical TEXT,
  seller_address TEXT,
  buyer_address TEXT,
  seller_sig TEXT,
  buyer_sig TEXT,
  expiry INTEGER,
  asset TEXT,
  amount_ap_str TEXT
);

-- Tabla existente: outbox
CREATE TABLE outbox (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  offer_id TEXT NOT NULL UNIQUE,
  state TEXT NOT NULL,
  last_error TEXT,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
```

##### **E. Nuevos Endpoints del Backend**

```javascript
// POST /wallet/create
// Crea un nuevo wallet
app.post('/wallet/create', async (req, res) => {
  // 1. Generar frase de 10 palabras
  const phrase10 = generatePhrase10();
  
  // 2. Calcular hash de la frase
  const phraseHash = hashPhrase(phrase10);
  
  // 3. Generar wallet ECDSA secp256k1
  const userWallet = ethers.Wallet.createRandom();
  const privateKey = userWallet.privateKey;
  const publicKey = userWallet.publicKey;
  const address = userWallet.address;
  
  // 4. Cifrar clave privada con AES-256-GCM
  const encryptedPrivateKey = encryptPrivateKey(privateKey);
  
  // 5. Generar session token
  const sessionToken = generateSessionToken();
  
  // 6. Guardar en DB
  db.run(`INSERT INTO users (...) VALUES (...)`, [
    phraseHash,
    encryptedPrivateKey,
    publicKey,
    address.toLowerCase(),
    sessionToken,
    now,
    now
  ]);
  
  // 7. Responder con frase y datos públicos
  res.status(200).json({
    phrase10: phrase10,           // ⚠️ Se envía UNA VEZ
    address: address.toLowerCase(),
    public_key: publicKey,
    session_token: sessionToken
  });
});

// POST /auth/login-via-phrase
// Restaura wallet usando frase de 10 palabras
app.post('/auth/login-via-phrase', async (req, res) => {
  const { phrase10 } = req.body;
  
  // 1. Validar formato (array de 10 palabras)
  if (!Array.isArray(phrase10) || phrase10.length !== 10) {
    return res.status(400).json({ error_code: 'BAD_REQUEST' });
  }
  
  // 2. Normalizar y calcular hash
  const phraseHash = hashPhrase(phrase10);
  
  // 3. Buscar usuario en DB
  db.get('SELECT * FROM users WHERE phrase10_hash = ?', [phraseHash], (err, row) => {
    if (!row) {
      return res.status(404).json({ error_code: 'NOT_FOUND' });
    }
    
    // 4. Generar nuevo session token
    const newSessionToken = generateSessionToken();
    
    // 5. Actualizar en DB
    db.run('UPDATE users SET session_token = ?, updated_at = ? WHERE id = ?', 
      [newSessionToken, now, row.id]
    );
    
    // 6. Responder con datos públicos
    res.status(200).json({
      address: row.address,
      public_key: row.public_key,
      session_token: newSessionToken
    });
  });
});

// GET /wallet/private-key
// Obtiene clave privada descifrada (requiere session token)
app.get('/wallet/private-key', async (req, res) => {
  const sessionToken = req.headers['x-session-token'];
  
  if (!sessionToken) {
    return res.status(401).json({ error_code: 'MISSING_SESSION_TOKEN' });
  }
  
  // Buscar usuario por session token
  db.get('SELECT encrypted_private_key FROM users WHERE session_token = ?', 
    [sessionToken], (err, row) => {
      if (!row) {
        return res.status(404).json({ error_code: 'SESSION_NOT_FOUND' });
      }
      
      // Descifrar clave privada
      const privateKey = decryptPrivateKey(row.encrypted_private_key);
      
      // Responder (⚠️ dato MUY sensible)
      res.status(200).json({
        private_key: privateKey
      });
    }
  );
});

// POST /wallet/identity-debug
// Debug endpoint para ver identidad (dev only)
app.post('/wallet/identity-debug', async (req, res) => {
  const { phrase10 } = req.body;
  
  // Similar a login-via-phrase pero retorna TAMBIÉN la clave privada
  // ⚠️ SOLO PARA DESARROLLO
  
  res.status(200).json({
    address: row.address,
    public_key: row.public_key,
    private_key: decryptPrivateKey(row.encrypted_private_key)
  });
});

// GET /v1/wallet/balance
// Obtiene balance de una dirección
app.get('/v1/wallet/balance', async (req, res) => {
  const { address } = req.query;
  
  try {
    const balance = await tokenContract.balanceOf(address);
    const decimals = await getDecimals();
    const balanceFormatted = ethers.formatUnits(balance, decimals);
    
    res.status(200).json({
      balance_ap: Math.floor(parseFloat(balanceFormatted))
    });
  } catch (error) {
    res.status(500).json({ error_code: 'BALANCE_ERROR' });
  }
});
```

---

## ✅ Lo que Funciona AHORA

### **1. Flujo Completo de Onboarding** ✅

```
Usuario instala app por primera vez
↓
WalletSetupScreen (pantalla de bienvenida)
↓
Usuario toca "Crear Wallet"
↓
App llama: POST /wallet/create
↓
Backend:
  - Genera frase de 10 palabras en español
  - Genera clave privada ECDSA
  - Cifra clave privada con AES-256-GCM
  - Guarda en DB con session token
  - Responde con: phrase10, address, public_key, session_token
↓
App muestra frase de 10 palabras
↓
Usuario confirma que guardó la frase
↓
App pide PIN de 4 dígitos
↓
App llama: GET /wallet/private-key (con session_token)
↓
Backend descifra y envía clave privada
↓
App cifra clave privada con Android Keystore
↓
App guarda wallet localmente (cifrado)
↓
Setup completado → navegar a app principal
```

### **2. Flujo de Restauración** ✅

```
Usuario tiene wallet existente
↓
WalletSetupScreen → toca "Ya tengo wallet"
↓
Usuario ingresa 10 palabras
↓
App llama: POST /auth/login-via-phrase
↓
Backend:
  - Normaliza frase y calcula hash
  - Busca usuario en DB
  - Genera nuevo session token
  - Responde con: address, public_key, session_token
↓
App llama: GET /wallet/private-key (con session_token)
↓
Backend descifra y envía clave privada
↓
App pide PIN de 4 dígitos (nuevo o existente)
↓
App cifra clave privada con Android Keystore
↓
App guarda wallet localmente (cifrado)
↓
Setup completado → navegar a app principal
```

### **3. Seguridad Implementada** ✅

**En Backend:**
- ✅ Claves privadas cifradas con AES-256-GCM
- ✅ Master key de 256 bits desde `.env`
- ✅ Frases de recuperación hasheadas (SHA-256)
- ✅ Session tokens aleatorios (32 bytes base64)
- ✅ Autenticación por session token

**En App:**
- ✅ Claves cifradas con Android Keystore
- ✅ Clave privada solo en memoria cuando desbloqueada
- ✅ `clearUnlockedWallet()` limpia memoria
- ✅ PIN de 4 dígitos (opcional biométrica)
- ✅ Session manager para tokens

### **4. Flujo de Pagos Offline** ✅ (Sin cambios)

El flujo de pagos offline con BLE + QR sigue funcionando igual que antes:

```
1. Vendedor genera QR con monto
2. Comprador escanea QR
3. Conexión BLE
4. Intercambio de PaymentTransaction
5. Ambos firman con sus claves privadas
6. Voucher guardado localmente
7. Sincronización automática cuando hay red
```

---

## ⚠️ Problemas y Limitaciones CRÍTICAS

### **🔴 1. SEGURIDAD - Clave Privada Viaja por Red**

```javascript
// GET /wallet/private-key
res.status(200).json({
  private_key: privateKey  // ⚠️ CLAVE PRIVADA EN PLAIN TEXT
});
```

**Problema**:
- La clave privada se envía **sin cifrar adicional** por HTTP/HTTPS
- Si alguien intercepta el tráfico → tiene acceso total a los fondos
- Session token puede ser robado también

**Soluciones**:
1. **Mejor**: NO enviar clave privada nunca. Backend firma transacciones.
2. **Intermedio**: Cifrado adicional con clave derivada del PIN del usuario
3. **Mínimo**: Asegurar HTTPS con certificate pinning

### **🔴 2. SEGURIDAD - Backend Custodia Claves**

```javascript
// Backend tiene TODAS las claves privadas cifradas
const encryptedPrivateKey = encryptPrivateKey(privateKey);
// Guardadas en BD con UNA master key
```

**Problema**:
- Si hackean el backend → tienen todas las claves cifradas
- Si roban `WALLET_MASTER_KEY` → pueden descifrar TODAS las claves
- Punto único de fallo

**Soluciones**:
1. **Mejor**: Claves generadas SOLO en cliente (Android Keystore)
2. **Intermedio**: Backend solo guarda clave pública, deriva clave privada desde frase en cliente
3. **Mínimo**: HSM (Hardware Security Module) para la master key

### **🔴 3. SEGURIDAD - Frase de 10 Palabras NO es BIP39**

```javascript
// 10 palabras de 2048 = 11 bits cada una
// Total: 110 bits de entropía
```

**Problema**:
- BIP39 estándar usa 12/24 palabras con checksum
- Sin checksum → typos no detectables
- No compatible con wallets estándar (MetaMask, Ledger, etc.)
- 110 bits < 128 bits recomendados para BIP39

**Soluciones**:
1. **Mejor**: Usar BIP39 completo (12 palabras en español)
2. **Intermedio**: Agregar checksum a las 10 palabras
3. **Mínimo**: Documentar claramente que NO es compatible con BIP39

### **🟡 4. UX - Frase Se Muestra UNA SOLA VEZ**

```kotlin
// WalletSetupScreen
SeedPhraseDisplayScreen(
    seedPhrase = body.phrase10,
    onConfirm = { viewModel.confirmSeedPhrase() }
)
```

**Problema**:
- Si el usuario no guarda la frase y pierde el teléfono → fondos perdidos
- No hay forma de recuperar la frase después
- Presión en el usuario para guardarla bien

**Soluciones**:
1. **Mejor**: Opción de "Ver frase de nuevo" en configuración (con autenticación fuerte)
2. **Intermedio**: Exportar backup cifrado con contraseña del usuario
3. **Mínimo**: Warning muy claro antes de continuar

### **🟡 5. ARQUITECTURA - Backend como Punto Único de Fallo**

```
App → Backend → Blockchain
      ↑
  Si backend cae:
  - No se pueden crear wallets
  - No se pueden restaurar wallets
  - No se pueden sincronizar vouchers
```

**Problema**:
- Backend debe estar 100% disponible
- Escalabilidad limitada
- Latencia añadida

**Soluciones**:
1. **Mejor**: Generación de wallets en cliente (Android Keystore)
2. **Intermedio**: Múltiples instancias del backend con load balancer
3. **Mínimo**: Modo degradado para operaciones offline

### **🟡 6. BASE DE DATOS - SQLite No Escala**

```javascript
const db = new sqlite3.Database('./vouchers.db');
```

**Problema**:
- SQLite es single-process
- No soporta concurrencia real
- Para piloto (< 100 usuarios) está bien
- Para producción es insuficiente

**Solución**: Migrar a PostgreSQL antes de producción.

### **🟡 7. FLUJO - Usuarios Reales Necesitan Fondos Iniciales**

```
Usuario crea wallet → balance = 0 AP
```

**Problema**:
- ¿Cómo obtienen AgroPuntos inicialmente?
- ¿Hay un faucet?
- ¿Compran con fiat?
- ¿Alguien les transfiere?

**Soluciones**:
1. **Faucet de prueba**: Para piloto, backend da 100 AP iniciales
2. **Compra con fiat**: Integración con payment gateway
3. **Distribución**: Organización distribuye a campesinos

### **🟡 8. PIN - Solo 4 Dígitos**

```kotlin
if (!pin.matches(Regex("\\d{4}"))) {
    // Error
}
```

**Problema**:
- 4 dígitos = 10,000 combinaciones
- Fácil de adivinar por fuerza bruta
- Sin rate limiting implementado

**Soluciones**:
1. **Mejor**: 6 dígitos + rate limiting (3 intentos, luego bloqueo temporal)
2. **Intermedio**: Biométrica obligatoria + PIN como fallback
3. **Mínimo**: 4 dígitos con rate limiting estricto

### **🟡 9. SESSION TOKENS - No Expiran**

```javascript
// Genera session token pero NO hay expiración
const sessionToken = generateSessionToken();
db.run('INSERT INTO users (..., session_token, ...) VALUES (...)', [sessionToken]);
```

**Problema**:
- Session tokens válidos para siempre
- Si alguien roba un token → acceso permanente

**Solución**:
1. **Mejor**: Expiration time (ej: 7 días) + refresh tokens
2. **Intermedio**: Invalidar al cambiar de dispositivo
3. **Mínimo**: Botón de "Cerrar todas las sesiones"

### **🟡 10. ENDPOINTS - `/wallet/identity-debug` Es PELIGROSO**

```javascript
// POST /wallet/identity-debug
res.status(200).json({
  address: row.address,
  public_key: row.public_key,
  private_key: decryptPrivateKey(row.encrypted_private_key)  // ⚠️⚠️⚠️
});
```

**Problema**:
- Endpoint que devuelve clave privada sin autenticación fuerte
- "Debug only" pero está en el código de producción
- Cualquiera con una frase puede sacar la clave

**Solución**:
1. **URGENTE**: Eliminar este endpoint en producción
2. **Intermedio**: Requiere password adicional del desarrollador
3. **Mínimo**: Solo disponible si `NODE_ENV=development`

---

## 📊 Comparación con Estado Anterior

| Aspecto | Estado Anterior | Estado Actual | Mejora |
|---------|----------------|---------------|---------|
| **Generación de Wallets** | ❌ Claves hardcodeadas | ✅ Generadas dinámicamente | ⭐⭐⭐⭐⭐ |
| **Frase de Recuperación** | ❌ No existía | ✅ 10 palabras español | ⭐⭐⭐⭐ |
| **Cifrado de Claves** | ❌ No había | ✅ AES-256-GCM + Keystore | ⭐⭐⭐⭐⭐ |
| **Sistema de Sesiones** | ❌ No existía | ✅ Session tokens | ⭐⭐⭐⭐ |
| **Onboarding UX** | ❌ No había | ✅ Flujo completo | ⭐⭐⭐⭐⭐ |
| **Seguridad General** | 2/10 | 6/10 | ⬆️ +4 |
| **Backend como Custodio** | ⚠️ Sí | ⚠️ Sí (pero cifrado) | ⬆️ +2 |
| **Balance Real** | ❌ Hardcodeado | ⚠️ Endpoint existe, no integrado | ⬆️ +1 |
| **Historial Funcional** | ❌ Vacío | ❌ Sigue vacío | = |
| **Tests** | ❌ 0 | ❌ 0 | = |
| **Listo para Piloto** | ❌ NO | ⚠️ CASI | ⬆️ |

---

## 🎯 Prioridades para Piloto

### **FASE 1: Seguridad Crítica (1-2 semanas)** 🔴 BLOQUEANTE

#### **1. Eliminar envío de clave privada por red** ⭐ PRIORIDAD #1

**Opción A (Recomendada)**: Derivación en cliente
```kotlin
// App: Deriva clave privada desde frase
fun derivePrivateKeyFromPhrase(phrase10: List<String>): String {
    val phraseString = phrase10.joinToString(" ")
    val seed = SHA256(phraseString)  // O usar PBKDF2
    val privateKey = secp256k1_derive(seed)
    return privateKey
}
```

**Backend solo guarda**:
- `phrase10_hash` (para login)
- `public_key` (para identificar)
- `address` (para identificar)
- ❌ NO guarda `encrypted_private_key`

**Opción B**: Cifrado adicional
```kotlin
// App: Cifra con clave derivada del PIN antes de enviar
val pinDerivedKey = PBKDF2(userPIN, salt, iterations=10000)
val encryptedForTransport = AES_encrypt(privateKey, pinDerivedKey)
```

**Esfuerzo**: 3-4 días  
**Impacto**: 🔴 CRÍTICO

#### **2. Eliminar endpoint `/wallet/identity-debug`** ⭐ PRIORIDAD #2

```javascript
// ELIMINAR en producción:
// app.post('/wallet/identity-debug', ...)

// O al menos:
if (process.env.NODE_ENV === 'production') {
  // NO registrar este endpoint
}
```

**Esfuerzo**: 1 hora  
**Impacto**: 🔴 ALTO

#### **3. Expiración de session tokens** ⭐ PRIORIDAD #3

```javascript
// users table
ALTER TABLE users ADD COLUMN session_expires_at INTEGER;

// Al generar token
const expiresAt = Math.floor(Date.now() / 1000) + (7 * 24 * 60 * 60); // 7 días
db.run('UPDATE users SET session_token = ?, session_expires_at = ? ...', 
  [sessionToken, expiresAt]
);

// Al verificar
db.get('SELECT * FROM users WHERE session_token = ? AND session_expires_at > ?',
  [sessionToken, now]
);
```

**Esfuerzo**: 1 día  
**Impacto**: 🟡 MEDIO

---

### **FASE 2: UX Esencial (1 semana)** 🟡

#### **4. Integrar balance real desde blockchain** ⭐ PRIORIDAD #4

```kotlin
// WalletViewModel
init {
    viewModelScope.launch {
        val address = SessionManager.getAddress(context)
        if (address != null) {
            val response = ApiClient.apiService.getWalletBalance(address)
            if (response.isSuccessful) {
                _availablePoints.value = response.body()!!.balance_ap
            }
        }
    }
}
```

**Esfuerzo**: 2 días  
**Impacto**: 🟡 ALTO

#### **5. Faucet inicial de tokens** ⭐ PRIORIDAD #5

```javascript
// POST /wallet/create
// Después de crear wallet, enviar tokens iniciales
const INITIAL_FAUCET_AMOUNT = 100; // 100 AP

// Transferir desde cuenta madre
const tx = await tokenContract.transfer(
    address,
    ethers.parseUnits(INITIAL_FAUCET_AMOUNT.toString(), decimals)
);
await tx.wait(CONFIRMATIONS);
```

**Esfuerzo**: 1 día  
**Impacto**: 🟡 CRÍTICO (para piloto)

#### **6. Mejorar seguridad del PIN** ⭐ PRIORIDAD #6

```kotlin
// 6 dígitos + rate limiting
class PinAttemptManager(context: Context) {
    private val prefs = context.getSharedPreferences("pin_attempts", MODE_PRIVATE)
    
    fun recordFailedAttempt(): Int {
        val attempts = prefs.getInt("failed_attempts", 0) + 1
        prefs.edit().putInt("failed_attempts", attempts).apply()
        
        if (attempts >= 3) {
            val lockUntil = System.currentTimeMillis() + (5 * 60 * 1000) // 5 min
            prefs.edit().putLong("lock_until", lockUntil).apply()
        }
        
        return attempts
    }
    
    fun isLocked(): Boolean {
        val lockUntil = prefs.getLong("lock_until", 0)
        return System.currentTimeMillis() < lockUntil
    }
    
    fun reset() {
        prefs.edit().clear().apply()
    }
}
```

**Esfuerzo**: 1-2 días  
**Impacto**: 🟡 MEDIO

---

### **FASE 3: Funcionalidad Completa (1 semana)** 🟢

#### **7. Historial funcional** ⭐ PRIORIDAD #7

```kotlin
// HistoryScreen - actualizado
val vouchers by voucherViewModel.allVouchers.collectAsState(initial = emptyList())

LazyColumn {
    items(vouchers) { voucher ->
        VoucherCard(
            voucher = voucher,
            onClick = { /* detalles */ }
        )
    }
}
```

**Esfuerzo**: 2 días  
**Impacto**: 🟢 MEDIO

#### **8. Opción de "Ver frase de nuevo"** ⭐ PRIORIDAD #8

**⚠️ Problema**: Backend NO guarda la frase, solo el hash.

**Solución A**: Guardar frase cifrada en backend
```javascript
// Cifrar frase con clave derivada de la frase misma
const phraseCipher = AES_encrypt(
    phrase10.join(' '),
    SHA256(phrase10.join(' '))
);
db.run('INSERT INTO users (..., encrypted_phrase, ...) VALUES (...)', 
  [phraseCipher]
);
```

**Solución B**: Advertencia más fuerte en primera vez
```
"⚠️ ESTA ES LA ÚNICA VEZ QUE VERÁS TU FRASE

Sin esta frase NO podrás recuperar tu wallet si pierdes tu teléfono.

□ La escribí en papel seguro
□ La guardé en un lugar seguro
□ Entiendo que nadie puede ayudarme a recuperarla

[Continuar solo si marcaste TODO]"
```

**Esfuerzo**: 2 días (Solución A) o 1 día (Solución B)  
**Impacto**: 🟢 ALTO

---

### **FASE 4: Testing y Pulido (1 semana)** 🧪

#### **9. Tests críticos**

```kotlin
// Tests de crypto
@Test
fun `phrase normalization is consistent with backend`() {
    // Verificar que normalización coincide
}

@Test
fun `private key derivation from phrase works`() {
    // Si se implementa derivación en cliente
}

// Tests de flujo
@Test
fun `wallet creation flow end to end`() {
    // Mock del backend, simular flujo completo
}

@Test
fun `wallet restore flow end to end`() {
    // Mock del backend, simular restauración
}
```

**Esfuerzo**: 3-4 días  
**Impacto**: 🟢 ALTO

#### **10. Manejo de errores de red**

```kotlin
// Retry automático con backoff
suspend fun <T> retryWithBackoff(
    times: Int = 3,
    initialDelay: Long = 1000,
    factor: Double = 2.0,
    block: suspend () -> T
): T {
    var currentDelay = initialDelay
    repeat(times - 1) {
        try {
            return block()
        } catch (e: IOException) {
            delay(currentDelay)
            currentDelay = (currentDelay * factor).toLong()
        }
    }
    return block() // último intento sin catch
}
```

**Esfuerzo**: 1-2 días  
**Impacto**: 🟢 MEDIO

---

## 📝 Checklist para Piloto

### **Seguridad** 🔴
- [ ] ❌ **CRÍTICO**: Eliminar envío de clave privada en plain text
- [ ] ❌ **CRÍTICO**: Eliminar `/wallet/identity-debug` en producción
- [ ] ❌ Session tokens con expiración
- [ ] ❌ Rate limiting en PIN (3 intentos → bloqueo 5 min)
- [ ] ✅ Claves cifradas con AES-256-GCM (backend)
- [ ] ✅ Claves cifradas con Android Keystore (app)
- [ ] ✅ Session tokens aleatorios

### **Funcionalidad** 🟡
- [ ] ❌ Balance real desde blockchain integrado
- [ ] ❌ Faucet inicial de tokens (100 AP)
- [ ] ❌ Historial funcional
- [ ] ❌ Opción de "Ver frase de nuevo" (segura)
- [ ] ✅ Crear wallet nuevo
- [ ] ✅ Restaurar wallet con frase
- [ ] ✅ Flujo de pagos offline (BLE + QR)
- [ ] ✅ Sincronización automática

### **UX** 🟢
- [ ] ❌ Advertencia fuerte sobre frase de recuperación
- [ ] ❌ Tutorial de primera vez
- [ ] ❌ Mensajes de error claros
- [ ] ⚠️ PIN solo 4 dígitos (mejorar a 6)
- [ ] ✅ Flujo de onboarding completo
- [ ] ✅ Pantallas modernas con Compose

### **Testing** 🧪
- [ ] ❌ Tests unitarios de crypto
- [ ] ❌ Tests de flujo end-to-end
- [ ] ❌ Tests en 2+ dispositivos reales
- [ ] ❌ Tests de concurrencia (múltiples usuarios)

### **Infraestructura** 🏗️
- [ ] ⚠️ SQLite (OK para piloto < 100 usuarios)
- [ ] ❌ Monitoring/logging
- [ ] ❌ Backup de base de datos
- [ ] ⚠️ Master key segura (usar HSM en producción)

---

## 🎯 Conclusión

### **Estado General**: 7/10 para Piloto ⭐⭐⭐⭐⭐⭐⭐

**Fortalezas**:
- ✅ Sistema de wallets completo y funcional
- ✅ Onboarding UX excelente
- ✅ Cifrado implementado (AES-256 + Keystore)
- ✅ Frases de recuperación en español
- ✅ Flujo de pagos offline ya funcionando

**Bloqueadores Críticos**:
- 🔴 Clave privada viaja por red sin cifrado adicional
- 🔴 Endpoint debug expone claves privadas
- 🟡 Balance no integrado (muestra hardcodeado)
- 🟡 Sin faucet inicial (usuarios sin fondos)

### **Tiempo Estimado para Piloto**: 2-3 semanas

**Semana 1**: Seguridad crítica (prioridades #1-#3)  
**Semana 2**: UX esencial (prioridades #4-#6)  
**Semana 3**: Pulido y testing (prioridades #7-#10)

### **Recomendación**: 

El proyecto está **MUY CERCA** de estar listo para un piloto controlado. Los bloqueadores críticos son **solucionables en 1-2 semanas**. 

**Plan sugerido**:
1. **Urgente (1 semana)**: Resolver seguridad crítica (#1-#3)
2. **Importante (1 semana)**: Integrar balance + faucet (#4-#5)
3. **Deseable (1 semana)**: Historial + advertencias (#7-#8)

Después de esto, el proyecto estaría **100% listo para piloto con 10-50 usuarios**.

---

**🎉 ¡Excelente trabajo hasta ahora! El avance es impresionante.**

