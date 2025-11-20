# 🛠️ Análisis Técnico Detallado del Backend

**Fecha**: 20 de noviembre de 2025  
**Proyecto**: AgroPuntos - Backend Node.js

---

## 📋 Tabla de Contenidos

1. [Resumen Técnico](#resumen-técnico)
2. [Arquitectura del Backend](#arquitectura-del-backend)
3. [Sistema de Wallets](#sistema-de-wallets)
4. [Seguridad y Cifrado](#seguridad-y-cifrado)
5. [Base de Datos](#base-de-datos)
6. [Endpoints API](#endpoints-api)
7. [Análisis de Vulnerabilidades](#análisis-de-vulnerabilidades)
8. [Recomendaciones](#recomendaciones)

---

## 🎯 Resumen Técnico

### **Stack Tecnológico**

```javascript
{
  "runtime": "Node.js 18+",
  "framework": "Express 4.18+",
  "database": "SQLite3",
  "blockchain": "Ethereum (Sepolia testnet)",
  "library": "ethers.js 6.x",
  "crypto": "Node.js crypto (AES-256-GCM)",
  "auth": "Session tokens (custom)"
}
```

### **Componentes Principales**

```
backend/
├── server.js (1404 líneas) ⭐ Core del servidor
├── crypto/
│   └── aes.js (116 líneas) ⭐ Cifrado de claves
├── utils/
│   ├── phraseGenerator.js (287 líneas) ⭐ Generación de frases
│   └── sessionToken.js (15 líneas) ⭐ Tokens de sesión
├── scripts/
│   └── settle_test_vector.js (Test de settle)
├── setTransaction.js (235 líneas) (Script de prueba)
└── vouchers.db (Base de datos SQLite)
```

---

## 🏗️ Arquitectura del Backend

### **Flujo General**

```
┌─────────────────────────────────────────────────────────────┐
│                       ANDROID APP                           │
└──────────────────┬──────────────────────────────────────────┘
                   │ HTTPS
                   ▼
┌─────────────────────────────────────────────────────────────┐
│                    EXPRESS SERVER                           │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  Rate Limiter (30 req/min)                          │   │
│  └─────────────────────────────────────────────────────┘   │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  Endpoints                                           │   │
│  │  • POST /wallet/create                              │   │
│  │  • POST /auth/login-via-phrase                      │   │
│  │  • GET /wallet/private-key                          │   │
│  │  • POST /wallet/identity-debug                      │   │
│  │  • POST /v1/vouchers                                │   │
│  │  • POST /v1/vouchers/settle                         │   │
│  │  • GET /v1/wallet/balance                           │   │
│  └─────────────────────────────────────────────────────┘   │
└──────────────────┬──────────────────────────────────────────┘
                   │
        ┌──────────┴──────────┐
        │                     │
        ▼                     ▼
┌───────────────┐     ┌──────────────────┐
│  SQLite DB    │     │  Ethereum Node   │
│               │     │  (via ethers.js) │
│  • users      │     │                  │
│  • vouchers   │     │  Sepolia Testnet │
│  • outbox     │     │  ERC-20 Contract │
└───────────────┘     └──────────────────┘
```

### **Conexión a Blockchain**

```javascript
// Soporte para múltiples RPCs con fallback automático
const urls = [
  process.env.RPC_URL_PRIMARY,   // Infura/Alchemy principal
  process.env.RPC_URL_SECONDARY, // Infura/Alchemy secundario
  process.env.RPC_URL_TERTIARY,  // Infura/Alchemy terciario
  process.env.RPC_URL_QUATERNARY // Fallback público
].filter(Boolean);

const sepoliaNet = ethers.Network.from(11155111);

const fallbacks = urls.map((u) => ({
  provider: new ethers.JsonRpcProvider(u, sepoliaNet, { staticNetwork: sepoliaNet }),
  weight: 1,
  stallTimeout: 1500
}));

const provider = new ethers.FallbackProvider(fallbacks);

// Wallet de la "cuenta madre"
const wallet = new ethers.Wallet(PRIVATE_KEY_CUENTA_MADRE, provider);

// Contrato ERC-20
const tokenContract = new ethers.Contract(CONTRACT_ADDRESS_AP, ERC20_ABI, wallet);
```

**Ventajas**:
- ✅ Fallback automático si un RPC falla
- ✅ Red fijada (evita auto-detección)
- ✅ Múltiples proveedores (resilencia)

**Desventajas**:
- ⚠️ Todos los RPCs deben estar configurados correctamente
- ⚠️ Latencia acumulada si el primario falla

---

## 🔐 Sistema de Wallets

### **1. Generación de Wallet** (`POST /wallet/create`)

#### **Flujo Completo**

```javascript
POST /wallet/create
{
  "device_info": "Samsung Galaxy A51" // Opcional
}

// Paso 1: Generar frase de 10 palabras en español
const phrase10 = generatePhrase10();
// Ejemplo: ["casa", "perro", "sol", "luna", "rio", "monte", "flor", "cielo", "mar", "tierra"]

// Paso 2: Normalizar y hashear la frase
const phraseNormalized = normalizePhrase(phrase10);
// "casa perro sol luna rio monte flor cielo mar tierra"

const phraseHash = crypto.createHash('sha256').update(phraseNormalized).digest('hex');
// "a1b2c3d4e5f6..." (64 caracteres hex)

// Paso 3: Generar wallet ECDSA secp256k1
const userWallet = ethers.Wallet.createRandom();
// privateKey: "0xabcdef1234567890..."
// publicKey: "0x04abcdef1234567890..." (sin comprimir)
// address: "0x1234567890abcdef1234567890abcdef12345678"

// Paso 4: Cifrar clave privada con AES-256-GCM
const encryptedPrivateKey = encryptPrivateKey(privateKey);
// "base64_encoded_iv_authtag_ciphertext"

// Paso 5: Generar session token
const sessionToken = crypto.randomBytes(32).toString('base64');
// "abc123def456..." (44 caracteres base64)

// Paso 6: Guardar en BD
db.run(`
  INSERT INTO users (
    phrase10_hash,
    encrypted_private_key,
    public_key,
    address,
    session_token,
    created_at,
    updated_at
  ) VALUES (?, ?, ?, ?, ?, ?, ?)
`, [phraseHash, encryptedPrivateKey, publicKey, address.toLowerCase(), sessionToken, now, now]);

// Paso 7: Responder
res.status(200).json({
  phrase10: phrase10,                 // ⚠️ SE ENVÍA UNA SOLA VEZ
  address: address.toLowerCase(),      // 0x...
  public_key: publicKey,               // 0x04...
  session_token: sessionToken          // Token para futuras peticiones
});
```

#### **Estructura de la Frase**

```javascript
// phraseGenerator.js
const SPANISH_WORDLIST = [
  'abajo', 'abrir', 'acero', ..., 'zurdo' // 2048 palabras
];

// Normalización de palabras
function normalizeWord(word) {
  return String(word)
    .trim()
    .toLowerCase()
    .normalize('NFD')              // Descompone caracteres con tildes
    .replace(/[\u0300-\u036f]/g, '') // Elimina diacríticos (tildes)
    .replace(/ñ/g, 'n')            // Reemplaza ñ por n
    .replace(/[^a-z0-9]/g, '');    // Elimina otros caracteres
}

// Generación aleatoria
function generatePhrase10() {
  const words = [];
  for (let i = 0; i < 10; i++) {
    const randomIndex = crypto.randomInt(0, SPANISH_WORDLIST.length);
    words.push(normalizeWord(SPANISH_WORDLIST[randomIndex]));
  }
  return words;
}
```

**Análisis de Seguridad**:
- ✅ 2048 palabras = 11 bits de entropía por palabra
- ✅ 10 palabras = 110 bits de entropía total
- ✅ Aproximadamente 2^110 ≈ 1.3 × 10^33 combinaciones
- ⚠️ NO es BIP39 (BIP39 usa 12/24 palabras con checksum)
- ⚠️ 110 bits < 128 bits recomendados por BIP39
- ⚠️ Sin checksum = typos no detectables

### **2. Login con Frase** (`POST /auth/login-via-phrase`)

```javascript
POST /auth/login-via-phrase
{
  "phrase10": ["casa", "perro", "sol", ..., "tierra"]
}

// Paso 1: Validar formato
if (!Array.isArray(phrase10) || phrase10.length !== 10) {
  return res.status(400).json({ error_code: 'BAD_REQUEST' });
}

// Paso 2: Normalizar y hashear
const phraseHash = hashPhrase(phrase10);

// Paso 3: Buscar en BD
db.get('SELECT * FROM users WHERE phrase10_hash = ?', [phraseHash], (err, row) => {
  if (!row) {
    return res.status(404).json({ error_code: 'NOT_FOUND' });
  }
  
  // Paso 4: Generar nuevo session token
  const newSessionToken = generateSessionToken();
  
  // Paso 5: Actualizar en BD
  db.run('UPDATE users SET session_token = ?, updated_at = ? WHERE id = ?', 
    [newSessionToken, now, row.id]
  );
  
  // Paso 6: Responder (SIN clave privada)
  res.status(200).json({
    address: row.address,
    public_key: row.public_key,
    session_token: newSessionToken
  });
});
```

**Ventajas**:
- ✅ No envía clave privada en este endpoint
- ✅ Genera nuevo session token (invalida el anterior)
- ✅ Normalización consistente

**Desventajas**:
- ⚠️ Session token no expira
- ⚠️ Sin rate limiting específico
- ⚠️ Sin protección contra timing attacks

### **3. Obtener Clave Privada** (`GET /wallet/private-key`)

```javascript
GET /wallet/private-key
Headers: {
  "X-Session-Token": "abc123def456..."
}

// Paso 1: Validar session token
const sessionToken = req.headers['x-session-token'];
if (!sessionToken) {
  return res.status(401).json({ error_code: 'MISSING_SESSION_TOKEN' });
}

// Paso 2: Buscar usuario
db.get('SELECT encrypted_private_key FROM users WHERE session_token = ?', 
  [sessionToken], (err, row) => {
    if (!row) {
      return res.status(404).json({ error_code: 'SESSION_NOT_FOUND' });
    }
    
    // Paso 3: Descifrar clave privada
    const privateKey = decryptPrivateKey(row.encrypted_private_key);
    
    // Paso 4: Responder
    res.status(200).json({
      private_key: privateKey  // ⚠️⚠️⚠️ DATO MUY SENSIBLE
    });
  }
);
```

**⚠️ VULNERABILIDADES CRÍTICAS**:
- 🔴 **Clave privada en plain text** sobre HTTPS
- 🔴 **Sin cifrado adicional** para el transporte
- 🔴 **Session token sin expiración** puede ser robado
- 🔴 **Sin rate limiting** específico en este endpoint
- 🔴 **Sin logging de accesos** (auditoría)

### **4. Debug de Identidad** (`POST /wallet/identity-debug`)

```javascript
POST /wallet/identity-debug
{
  "phrase10": ["casa", "perro", "sol", ..., "tierra"]
}

// Similar a login-via-phrase pero responde con TODO:
res.status(200).json({
  address: row.address,
  public_key: row.public_key,
  private_key: decryptPrivateKey(row.encrypted_private_key)  // ⚠️⚠️⚠️
});
```

**⚠️ PELIGRO EXTREMO**:
- 🔴 **Expone clave privada** sin autenticación fuerte
- 🔴 **Sin restricción de entorno** (production vs development)
- 🔴 **Sin logging** de quién accede
- 🔴 **Ataque**: Si alguien obtiene una frase → tiene la clave privada

**SOLUCIÓN URGENTE**:
```javascript
// Solo habilitar en desarrollo
if (process.env.NODE_ENV === 'production') {
  // NO registrar este endpoint en producción
} else {
  app.post('/wallet/identity-debug', ...);
}
```

---

## 🔒 Seguridad y Cifrado

### **1. Cifrado AES-256-GCM** (`crypto/aes.js`)

```javascript
// Configuración
const ALGORITHM = 'aes-256-gcm';
const IV_LENGTH = 16;          // 16 bytes para GCM
const AUTH_TAG_LENGTH = 16;    // 16 bytes para el tag de autenticación
const MASTER_KEY = process.env.WALLET_MASTER_KEY; // 32 bytes (256 bits)

// Cifrado
function encryptPrivateKey(plainHex) {
  const cleanHex = plainHex.startsWith('0x') ? plainHex.slice(2) : plainHex;
  const plainBuffer = Buffer.from(cleanHex, 'hex');
  
  // IV aleatorio
  const iv = crypto.randomBytes(IV_LENGTH);
  
  // Crear cipher
  const cipher = crypto.createCipheriv(ALGORITHM, masterKeyBuffer, iv);
  
  // Cifrar
  const encrypted = Buffer.concat([
    cipher.update(plainBuffer),
    cipher.final()
  ]);
  
  // Obtener auth tag (GCM)
  const authTag = cipher.getAuthTag();
  
  // Combinar: IV + authTag + encrypted
  const combined = Buffer.concat([iv, authTag, encrypted]);
  
  return combined.toString('base64');
}

// Descifrado
function decryptPrivateKey(cipherText) {
  const combined = Buffer.from(cipherText, 'base64');
  
  // Extraer componentes
  const iv = combined.slice(0, IV_LENGTH);
  const authTag = combined.slice(IV_LENGTH, IV_LENGTH + AUTH_TAG_LENGTH);
  const encrypted = combined.slice(IV_LENGTH + AUTH_TAG_LENGTH);
  
  // Crear decipher
  const decipher = crypto.createDecipheriv(ALGORITHM, masterKeyBuffer, iv);
  decipher.setAuthTag(authTag);
  
  // Descifrar
  const decrypted = Buffer.concat([
    decipher.update(encrypted),
    decipher.final()
  ]);
  
  return '0x' + decrypted.toString('hex');
}
```

**Análisis de Seguridad**:

✅ **Fortalezas**:
- AES-256-GCM es estándar de la industria
- GCM proporciona autenticación (detecta tampering)
- IV aleatorio por cada cifrado (evita ataques de repetición)
- Auth tag de 16 bytes (seguro)

⚠️ **Debilidades**:
- **Master key única** para TODAS las claves privadas
- Si `WALLET_MASTER_KEY` se filtra → TODO comprometido
- Master key en `.env` (no en HSM)
- Sin rotación de master key
- Sin backup de master key cifrado

**Recomendaciones**:
1. **HSM (Hardware Security Module)** para la master key en producción
2. **Key rotation**: Cambiar master key periódicamente
3. **KMS (Key Management Service)**: AWS KMS, Google Cloud KMS, Azure Key Vault
4. **Multi-layer encryption**: Cifrar master key con otra clave

### **2. Validación de Master Key**

```javascript
// Verificar que la master key sea válida
let masterKeyBuffer;
if (MASTER_KEY.length === 64 && /^[0-9a-fA-F]+$/.test(MASTER_KEY)) {
  // Hex string de 64 caracteres = 32 bytes
  masterKeyBuffer = Buffer.from(MASTER_KEY, 'hex');
} else if (MASTER_KEY.length === 44) {
  // Base64 de 44 caracteres = 32 bytes
  masterKeyBuffer = Buffer.from(MASTER_KEY, 'base64');
} else {
  // Derivar clave de 32 bytes usando SHA-256
  masterKeyBuffer = crypto.createHash('sha256').update(MASTER_KEY).digest();
}

if (masterKeyBuffer.length !== 32) {
  console.error('❌ ERROR: WALLET_MASTER_KEY debe derivar a 32 bytes (256 bits)');
  process.exit(1);
}
```

**Ventajas**:
- ✅ Acepta diferentes formatos (hex, base64, texto)
- ✅ Valida longitud correcta
- ✅ Sale con error si es inválida

**Desventajas**:
- ⚠️ Si se usa texto plano, solo tiene la entropía del texto
- ⚠️ SHA-256 de texto débil = clave débil

---

## 💾 Base de Datos

### **Esquema SQLite**

```sql
-- Tabla de usuarios
CREATE TABLE IF NOT EXISTS users (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  phrase10_hash TEXT NOT NULL UNIQUE,     -- SHA-256 de la frase normalizada
  encrypted_private_key TEXT NOT NULL,    -- Clave cifrada con AES-256-GCM (base64)
  public_key TEXT NOT NULL,               -- Clave pública (0x04...)
  address TEXT NOT NULL UNIQUE,           -- Dirección Ethereum (0x...)
  session_token TEXT,                     -- Token de sesión (base64)
  created_at INTEGER NOT NULL,            -- Timestamp Unix
  updated_at INTEGER NOT NULL             -- Timestamp Unix
);

CREATE INDEX idx_phrase10_hash ON users(phrase10_hash);
CREATE INDEX idx_session_token ON users(session_token);
CREATE INDEX idx_address ON users(address);

-- Tabla de vouchers (pagos offline)
CREATE TABLE IF NOT EXISTS vouchers (
  offer_id TEXT PRIMARY KEY,              -- UUID v4
  amount_ap INTEGER NOT NULL,             -- Cantidad de AgroPuntos
  buyer_alias TEXT NOT NULL,              -- Alias del comprador
  seller_alias TEXT NOT NULL,             -- Alias del vendedor
  tx_hash TEXT,                           -- Hash de transacción blockchain
  status TEXT NOT NULL,                   -- Estado interno
  onchain_status TEXT,                    -- Estado on-chain
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);

-- Tabla outbox (patrón outbox para transacciones pendientes)
CREATE TABLE IF NOT EXISTS outbox (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  offer_id TEXT NOT NULL UNIQUE,          -- Referencia a vouchers
  state TEXT NOT NULL,                    -- 'PENDING', 'PROCESSING', 'DONE', 'FAILED'
  last_error TEXT,                        -- Último error si falló
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
```

### **Migraciones Ad-Hoc**

```javascript
// Agregar columna session_token si no existe
db.run(`ALTER TABLE users ADD COLUMN session_token TEXT`, () => {
  // Ignora error si ya existe
});
```

**⚠️ Problema**:
- No hay sistema de migraciones formal
- `ALTER TABLE` puede fallar silenciosamente
- Difícil trackear versión del schema

**Recomendación**: Usar sistema de migraciones formal:
- Sequelize (ORM con migraciones)
- node-migrate (migración pura)
- Typeorm (TypeScript + migraciones)

### **Índices**

```sql
CREATE INDEX idx_phrase10_hash ON users(phrase10_hash);
CREATE INDEX idx_session_token ON users(session_token);
CREATE INDEX idx_address ON users(address);
```

**Análisis**:
- ✅ Índices en columnas usadas para búsqueda
- ✅ `phrase10_hash` es UNIQUE (no duplicados)
- ✅ `address` es UNIQUE (no duplicados)
- ⚠️ `session_token` no es UNIQUE (puede haber duplicados temporales durante update)

---

## 🌐 Endpoints API

### **Resumen de Endpoints**

| Método | Ruta | Autenticación | Propósito |
|--------|------|---------------|-----------|
| POST | `/wallet/create` | ❌ Ninguna | Crear nuevo wallet |
| POST | `/auth/login-via-phrase` | ❌ Ninguna | Restaurar wallet con frase |
| GET | `/wallet/private-key` | ✅ Session Token | Obtener clave privada |
| POST | `/wallet/identity-debug` | ❌ Ninguna | Debug (ver identidad) |
| POST | `/v1/vouchers` | ❌ Ninguna | Crear voucher online |
| POST | `/v1/vouchers/settle` | ❌ Ninguna | Liquidar voucher offline |
| GET | `/v1/tx/{offer_id}` | ❌ Ninguna | Estado de transacción |
| GET | `/v1/wallet/balance` | ❌ Ninguna | Balance de una dirección |
| GET | `/v1/balance/{alias}` | ❌ Ninguna | Balance por alias (deprecated) |

### **Rate Limiting**

```javascript
const limiter = rateLimit({
  windowMs: parseInt(process.env.RATE_LIMIT_WINDOW_MS) || 60000,  // 1 minuto
  max: parseInt(process.env.RATE_LIMIT_MAX_REQUESTS) || 30,       // 30 requests
  message: 'Demasiadas solicitudes, intenta más tarde'
});
app.use('/v1/', limiter);
```

**Problema**:
- ⚠️ Solo aplica a rutas `/v1/*`
- ⚠️ `/wallet/*` y `/auth/*` NO tienen rate limiting
- ⚠️ 30 req/min es generoso para un atacante

**Solución**:
```javascript
// Rate limiting más estricto para endpoints sensibles
const strictLimiter = rateLimit({
  windowMs: 60000,  // 1 minuto
  max: 5,           // 5 intentos
  skipSuccessfulRequests: true
});

app.post('/auth/login-via-phrase', strictLimiter, ...);
app.get('/wallet/private-key', strictLimiter, ...);
```

---

## ⚠️ Análisis de Vulnerabilidades

### **🔴 Vulnerabilidades CRÍTICAS**

#### **1. Clave Privada Viaja Sin Cifrado Adicional**

```javascript
// GET /wallet/private-key
res.status(200).json({
  private_key: "0xabcdef1234567890..."  // ⚠️ Plain text sobre HTTPS
});
```

**Impacto**: 🔴 CRÍTICO  
**Probabilidad**: 🟡 MEDIA (requiere interceptación de tráfico)  
**CVSS Score**: 8.5/10

**Ataque**:
- Atacante intercepta tráfico HTTPS (MITM, WiFi público, proxy malicioso)
- Obtiene clave privada
- Puede transferir TODOS los fondos

**Solución**:
1. **Mejor**: NO enviar clave privada nunca. Backend firma transacciones.
2. **Intermedio**: Cifrado adicional con clave derivada del PIN.
3. **Mínimo**: Certificate pinning en la app.

#### **2. Backend Custodia Todas las Claves**

**Impacto**: 🔴 CRÍTICO  
**Probabilidad**: 🟡 MEDIA (requiere hackear backend)  
**CVSS Score**: 9.0/10

**Ataque**:
- Atacante obtiene acceso al servidor backend
- Lee `WALLET_MASTER_KEY` de `.env`
- Descifra TODAS las claves privadas de la BD
- Roba TODOS los fondos de TODOS los usuarios

**Solución**:
1. **Mejor**: Claves generadas SOLO en cliente (Android Keystore).
2. **Intermedio**: HSM para la master key.
3. **Mínimo**: Segmentación (master keys diferentes por región/grupo).

#### **3. Endpoint Debug Expone Claves**

```javascript
// POST /wallet/identity-debug
// ⚠️ Sin restricción de entorno
res.status(200).json({
  private_key: decryptPrivateKey(...)  // ⚠️⚠️⚠️
});
```

**Impacto**: 🔴 CRÍTICO  
**Probabilidad**: 🔴 ALTA (endpoint público)  
**CVSS Score**: 9.5/10

**Ataque**:
- Atacante obtiene una frase de 10 palabras (phishing, shoulder surfing)
- Llama a `/wallet/identity-debug` con la frase
- Obtiene clave privada
- Roba fondos

**Solución**:
```javascript
if (process.env.NODE_ENV === 'production') {
  // NO registrar este endpoint
} else {
  app.post('/wallet/identity-debug', ...);
}
```

---

### **🟡 Vulnerabilidades ALTAS**

#### **4. Session Tokens No Expiran**

**Impacto**: 🟡 ALTO  
**Probabilidad**: 🟡 MEDIA  
**CVSS Score**: 6.5/10

**Ataque**:
- Atacante roba session token (XSS, MITM, malware)
- Token válido para siempre
- Acceso perpetuo a la clave privada

**Solución**:
```javascript
const EXPIRATION_DAYS = 7;
const expiresAt = Math.floor(Date.now() / 1000) + (EXPIRATION_DAYS * 24 * 60 * 60);

db.run('UPDATE users SET session_token = ?, session_expires_at = ? ...', 
  [sessionToken, expiresAt]
);

// Al verificar
db.get('SELECT * FROM users WHERE session_token = ? AND session_expires_at > ?',
  [sessionToken, now]
);
```

#### **5. Sin Rate Limiting en Endpoints Sensibles**

**Impacto**: 🟡 ALTO  
**Probabilidad**: 🔴 ALTA  
**CVSS Score**: 7.0/10

**Ataque**:
- Brute force de frases de 10 palabras
- Adivinanza de session tokens
- DoS (Denial of Service)

**Solución**:
```javascript
const strictLimiter = rateLimit({
  windowMs: 60000,  // 1 minuto
  max: 5,           // 5 intentos
  skipSuccessfulRequests: true
});

app.post('/auth/login-via-phrase', strictLimiter, ...);
app.get('/wallet/private-key', strictLimiter, ...);
```

#### **6. Frase de 10 Palabras No es BIP39**

**Impacto**: 🟡 MEDIO  
**Probabilidad**: 🟢 BAJA  
**CVSS Score**: 5.0/10

**Problema**:
- 110 bits < 128 bits recomendados por BIP39
- Sin checksum → typos no detectables
- No compatible con wallets estándar

**Solución**:
```javascript
// Usar BIP39 completo (12 palabras con checksum)
const bip39 = require('bip39');

// Generar mnemonic
const mnemonic = bip39.generateMnemonic(128, null, bip39.wordlists.spanish);
// "casa perro sol luna rio monte flor cielo mar tierra viento fuego"

// Derivar clave privada
const seed = bip39.mnemonicToSeedSync(mnemonic);
const hdNode = ethers.HDNodeWallet.fromSeed(seed);
const wallet = hdNode.derivePath("m/44'/60'/0'/0/0"); // Ethereum path
```

---

### **🟢 Vulnerabilidades MEDIAS**

#### **7. SQLite No Escala**

**Impacto**: 🟢 MEDIO  
**Probabilidad**: 🔴 ALTA (en producción con muchos usuarios)  
**CVSS Score**: 4.0/10

**Problema**:
- SQLite es single-process
- No soporta concurrencia real
- Límite de ~1000 writes/segundo
- Para piloto (< 100 usuarios) está bien

**Solución**:
```javascript
// Migrar a PostgreSQL
const { Pool } = require('pg');
const pool = new Pool({
  connectionString: process.env.DATABASE_URL
});
```

#### **8. Sin Logging de Auditoría**

**Impacto**: 🟢 MEDIO  
**Probabilidad**: 🟡 MEDIA  
**CVSS Score**: 4.5/10

**Problema**:
- Sin logs de quién accede a claves privadas
- Difícil detectar brechas de seguridad
- Sin compliance (GDPR, SOC2, etc.)

**Solución**:
```javascript
const winston = require('winston');

const logger = winston.createLogger({
  level: 'info',
  format: winston.format.json(),
  transports: [
    new winston.transports.File({ filename: 'audit.log' })
  ]
});

// Log de accesos sensibles
logger.info('Private key accessed', {
  address: row.address,
  session_token: sessionToken,
  ip: req.ip,
  timestamp: new Date().toISOString()
});
```

---

## 📊 Scorecard de Seguridad

| Aspecto | Estado | Prioridad |
|---------|--------|-----------|
| **Cifrado de claves** | 🟡 AES-256-GCM (pero master key única) | 🔴 ALTA |
| **Transporte de clave privada** | 🔴 Plain text sobre HTTPS | 🔴 CRÍTICA |
| **Endpoint debug** | 🔴 Expone claves sin restricción | 🔴 CRÍTICA |
| **Session tokens** | 🟡 Sin expiración | 🟡 ALTA |
| **Rate limiting** | 🟡 Parcial (solo /v1/*) | 🟡 ALTA |
| **Frase de recuperación** | 🟡 110 bits, sin checksum | 🟢 MEDIA |
| **Base de datos** | 🟡 SQLite (no escala) | 🟢 MEDIA |
| **Logging** | 🔴 No hay auditoría | 🟢 MEDIA |
| **Master key** | 🔴 En .env (no HSM) | 🔴 ALTA |
| **Fallback RPC** | ✅ Múltiples RPCs | ✅ OK |

**Score Global**: 5.5/10 🟡

---

## 🎯 Recomendaciones

### **URGENTE (1 semana)**

1. **Eliminar envío de clave privada**  
   - Implementar derivación en cliente  
   - O cifrado adicional con clave derivada del PIN

2. **Eliminar `/wallet/identity-debug` en producción**  
   ```javascript
   if (process.env.NODE_ENV !== 'development') {
     // NO registrar este endpoint
   }
   ```

3. **Expiración de session tokens**  
   - 7 días de validez  
   - Refresh tokens para renovar

### **ALTA PRIORIDAD (2 semanas)**

4. **Rate limiting estricto**  
   - 5 intentos/minuto en endpoints sensibles  
   - Bloqueo temporal después de fallos

5. **Logging de auditoría**  
   - Winston o Bunyan  
   - Logs de accesos a claves privadas

6. **Migrar a BIP39**  
   - 12 palabras con checksum  
   - Compatible con wallets estándar

### **MEDIA PRIORIDAD (1 mes)**

7. **HSM para master key**  
   - AWS KMS, Google Cloud KMS, o Azure Key Vault  
   - Rotación de claves

8. **Migrar a PostgreSQL**  
   - Antes de producción con > 100 usuarios  
   - Replicación y backup

9. **Tests de seguridad**  
   - Penetration testing  
   - Security audit externo

---

## 📈 Conclusión

El backend ha avanzado **significativamente** con la implementación del sistema de wallets. Sin embargo, existen **vulnerabilidades críticas** que deben ser resueltas antes de un lanzamiento en producción.

**Para un piloto controlado (10-50 usuarios)**: El estado actual es **ACEPTABLE** si se resuelven los 3 puntos urgentes.

**Para producción (100+ usuarios)**: Se requieren TODAS las recomendaciones implementadas.

**Score de Madurez**: 6.5/10 ⭐⭐⭐⭐⭐⭐  
(Era 3/10 antes de las nuevas features)

**Tiempo estimado para producción-ready**: 4-6 semanas

---

