// Cargar .env desde la raíz del proyecto o desde backend/
const path = require('path');
require('dotenv').config({ path: path.resolve(__dirname, '../.env') });
require('dotenv').config({ path: path.resolve(__dirname, '.env') }); // Fallback a backend/.env
const express = require('express');
const cors = require('cors');
const { ethers } = require('ethers');
const sqlite3 = require('sqlite3').verbose();
const rateLimit = require('express-rate-limit');
const { encryptPrivateKey, decryptPrivateKey } = require('./crypto/aes');
const { generatePhrase10, normalizePhrase, hashPhrase } = require('./utils/phraseGenerator');
const { generateSessionToken } = require('./utils/sessionToken');

const app = express();
const PORT = process.env.PORT || 3000;

// ───────────────────────────── Middleware ─────────────────────────────
app.use(express.json());
app.use(cors());

// Rate limiting general
const limiter = rateLimit({
  windowMs: parseInt(process.env.RATE_LIMIT_WINDOW_MS) || 60000,
  max: parseInt(process.env.RATE_LIMIT_MAX_REQUESTS) || 30,
  message: 'Demasiadas solicitudes, intenta más tarde'
});
app.use('/v1/', limiter);

// Rate limiting ESTRICTO para settle (endpoint crítico de seguridad)
const settleLimiter = rateLimit({
  windowMs: 60000, // 1 minuto
  max: 10, // Máximo 10 settle requests por minuto
  message: 'Demasiados intentos de settle, intenta más tarde',
  handler: (req, res) => {
    console.warn(`🚨 [RATE_LIMIT] IP bloqueada temporalmente en settle: ${req.ip}`);
    res.status(429).json({
      error_code: 'RATE_LIMIT_EXCEEDED',
      message: 'Demasiados intentos de settle. Espera 1 minuto.'
    });
  }
});

// ─────────────────────── Configuración blockchain ─────────────────────
const CHAIN_ID = parseInt(process.env.CHAIN_ID || '11155111');
const PRIVATE_KEY = process.env.PRIVATE_KEY_CUENTA_MADRE;
const CONTRACT_ADDRESS = process.env.CONTRACT_ADDRESS_AP_V2 || process.env.CONTRACT_ADDRESS_AP;
const CONFIRMATIONS = parseInt(process.env.CONFIRMATIONS || '1');

// TEMPORAL: Usar un solo RPC para evitar problemas de quorum
// Para producción: considerar plan pago de Infura/Alchemy
const RPC_URL = process.env.RPC_URL_PRIMARY || process.env.RPC_URL || 'https://rpc.sepolia.org';

if (!PRIVATE_KEY || !CONTRACT_ADDRESS || !RPC_URL) {
  console.error('❌ ERROR: Faltan variables de entorno necesarias');
  console.error('Verifica PRIVATE_KEY_CUENTA_MADRE, CONTRACT_ADDRESS_AP y RPC_URL');
  process.exit(1);
}

// Crear proveedor simple (sin fallback para evitar problemas de quorum)
const sepoliaNet = ethers.Network.from(11155111);

const provider = new ethers.JsonRpcProvider(
  RPC_URL,
  sepoliaNet,
  { staticNetwork: sepoliaNet }
);


// Conectar la wallet (cuenta madre)
const wallet = new ethers.Wallet(PRIVATE_KEY, provider);
console.log(`✅ Cuenta madre conectada: ${wallet.address}`);
console.log(`🌐 RPC configurado: ${RPC_URL}`);

// ABI mínimo del ERC-20
const ERC20_PERMIT_ABI = [
  "function transfer(address to, uint256 amount) external returns (bool)",
  "function transferFrom(address from, address to, uint256 amount) external returns (bool)",
  "function balanceOf(address account) external view returns (uint256)",
  "function allowance(address owner, address spender) external view returns (uint256)",
  "function decimals() external view returns (uint8)",
  "function mint(address to, uint256 amount) external",
  // EIP-2612 (permit)
  "function permit(address owner, address spender, uint256 value, uint256 deadline, uint8 v, bytes32 r, bytes32 s) external",
  "function nonces(address owner) external view returns (uint256)",
  "function DOMAIN_SEPARATOR() external view returns (bytes32)"
];

// Instanciar contrato AgroPuntos
const tokenContract = new ethers.Contract(CONTRACT_ADDRESS, ERC20_PERMIT_ABI, wallet);
console.log(`🔗 Contrato conectado: ${CONTRACT_ADDRESS}`);

// Límites offline eliminados - sin restricciones de monto

let DECIMALS_CACHE = null;
async function getDecimals() {
  if (DECIMALS_CACHE === null) {
    DECIMALS_CACHE = await tokenContract.decimals();
  }
  return DECIMALS_CACHE;
}

// ─────────────────────── Mutex para transferFrom() ────────────────────
/**
 * Mutex simple para serializar operaciones asíncronas.
 * Asegura que solo una operación crítica se ejecute a la vez.
 */
class Mutex {
  constructor() {
    this._queue = [];
    this._locked = false;
  }

  async acquire() {
    return new Promise((resolve) => {
      if (!this._locked) {
        this._locked = true;
        resolve();
      } else {
        this._queue.push(resolve);
      }
    });
  }

  release() {
    if (this._queue.length > 0) {
      const resolve = this._queue.shift();
      resolve();
    } else {
      this._locked = false;
    }
  }

  async runExclusive(callback) {
    await this.acquire();
    try {
      return await callback();
    } finally {
      this.release();
    }
  }
}

// Mutex global para serializar transferFrom() y evitar conflictos de nonce
// en la cuenta madre cuando se procesan múltiples vouchers simultáneamente
const transferFromMutex = new Mutex();
console.log('🔒 Mutex inicializado para transferFrom() secuencial');

// Map de mutexes por usuario para serializar transacciones del mismo comprador
// Esto asegura que transacciones offline consecutivas (nonce 0, 1, 2...) se procesen en orden
const userMutexes = new Map();

function getUserMutex(userAddress) {
  const address = userAddress.toLowerCase();
  if (!userMutexes.has(address)) {
    userMutexes.set(address, new Mutex());
    console.log(`🔒 Nuevo mutex creado para usuario: ${address}`);
  }
  return userMutexes.get(address);
}

// ─────────────────────────── Base de datos ────────────────────────────
const db = new sqlite3.Database('./vouchers.db', (err) => {
  if (err) {
    console.error('Error abriendo base de datos:', err);
  } else {
    console.log('Conectado a SQLite');
    initDatabase();
  }
});

function initDatabase() {
  db.run(`
    CREATE TABLE IF NOT EXISTS vouchers (
      offer_id TEXT PRIMARY KEY,
      amount_ap INTEGER NOT NULL,
      buyer_alias TEXT NOT NULL,
      seller_alias TEXT NOT NULL,
      tx_hash TEXT,
      status TEXT NOT NULL,
      onchain_status TEXT,
      created_at INTEGER NOT NULL,
      updated_at INTEGER NOT NULL
    )
  `, (err) => {
    if (err) {
      console.error('Error creando tabla:', err);
    } else {
      console.log('Tabla vouchers creada/verificada');
      migrateForOfflineSchema();
    }
  });

  // Crear tabla users para TRUE SELF-CUSTODY model
  // Backend SOLO almacena datos públicos (address, public_key)
  // Backend NUNCA almacena: phrase10_hash, encrypted_private_key
  db.run(`
    CREATE TABLE IF NOT EXISTS users (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      address TEXT NOT NULL UNIQUE,
      public_key TEXT NOT NULL,
      session_token TEXT,
      session_expires_at INTEGER,
      created_at INTEGER NOT NULL,
      updated_at INTEGER NOT NULL
    )
  `, (err) => {
    if (err) {
      console.error('Error creando tabla users:', err);
    } else {
      console.log('✅ Tabla users creada/verificada (TRUE SELF-CUSTODY model)');
      
      // Crear índices para mejor rendimiento
      db.run('CREATE INDEX IF NOT EXISTS idx_address ON users(address)', (err) => {
        if (err && !err.message.includes('already exists')) {
          console.error('Error creando índice idx_address:', err);
        }
      });
      
      db.run('CREATE INDEX IF NOT EXISTS idx_session_token ON users(session_token)', (err) => {
        if (err && !err.message.includes('already exists')) {
          console.error('Error creando índice idx_session_token:', err);
        }
      });
    }
  });
}

// ─────────────────────── Helpers de outbox/migraciones ───────────────────────
function migrateForOfflineSchema() {
  db.run(`ALTER TABLE vouchers ADD COLUMN payload_canonical TEXT`, () => {});
  db.run(`ALTER TABLE vouchers ADD COLUMN seller_address TEXT`, () => {});
  db.run(`ALTER TABLE vouchers ADD COLUMN buyer_address TEXT`, () => {});
  db.run(`ALTER TABLE vouchers ADD COLUMN seller_sig TEXT`, () => {});
  db.run(`ALTER TABLE vouchers ADD COLUMN buyer_sig TEXT`, () => {});
  db.run(`ALTER TABLE vouchers ADD COLUMN expiry INTEGER`, () => {});
  db.run(`ALTER TABLE vouchers ADD COLUMN asset TEXT`, () => {});
  db.run(`ALTER TABLE vouchers ADD COLUMN amount_ap_str TEXT`, () => {});
  // Columnas para EIP-2612 (permit)
  db.run(`ALTER TABLE vouchers ADD COLUMN permit_tx_hash TEXT`, () => {});
  db.run(`ALTER TABLE vouchers ADD COLUMN transfer_tx_hash TEXT`, () => {});
  db.run(`
    CREATE TABLE IF NOT EXISTS outbox (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      offer_id TEXT NOT NULL UNIQUE,
      state TEXT NOT NULL,
      last_error TEXT,
      created_at INTEGER NOT NULL,
      updated_at INTEGER NOT NULL
    )
  `, () => {});
}

function enqueueOutbox(offer_id, cb) {
  const ts = Math.floor(Date.now() / 1000);
  db.run(
    `INSERT OR IGNORE INTO outbox(offer_id, state, created_at, updated_at) VALUES(?, 'PENDING', ?, ?)`,
    [offer_id, ts, ts],
    cb
  );
}

function markOutbox(offer_id, state, errMsg, cb) {
  const ts = Math.floor(Date.now() / 1000);
  db.run(
    `UPDATE outbox SET state=?, last_error=?, updated_at=? WHERE offer_id=?`,
    [state, errMsg || null, ts, offer_id],
    cb
  );
}

// ─────────────────────────── Alias opcionales ─────────────────────────
const aliasToAddress = {
  // Puedes dejarlo vacío o registrar algunos alias de prueba.
  VendedorTest: '0x8846f77a51371269a9e84310cc978154adbf7cf8',
};

function getAddressFromAlias(alias) {
  return aliasToAddress[alias] || null;
}

// Aceptar 0x directo o alias registrado
function isHexAddress(str) {
  return /^0x[a-fA-F0-9]{40}$/.test((str || "").trim());
}

function resolveAddress(aliasOrAddress) {
  const v = (aliasOrAddress || "").trim();
  if (isHexAddress(v)) return v;        // permite 0x... directamente
  const addr = aliasToAddress[v];
  if (!addr) throw new Error(`ALIAS_NOT_FOUND:${v}`);
  return addr;
}

// ─────────────────────── Utilidades canónicas y firmas ───────────────────────
function canonicalizePaymentBase(base) {
  const required = ['offer_id', 'amount_ap', 'asset', 'expiry', 'seller_address', 'buyer_address'];
  for (const k of required) {
    if (base[k] === undefined || base[k] === null) throw new Error(`MISSING_${k}`);
  }

  const amountStr = String(base.amount_ap);
  if (!/^\d+(\.\d+)?$/.test(amountStr)) throw new Error('BAD_AMOUNT_FORMAT');

  const payload = {
    asset: String(base.asset),
    buyer_address: String(base.buyer_address).toLowerCase(),
    expiry: Number(base.expiry),
    offer_id: String(base.offer_id),
    seller_address: String(base.seller_address).toLowerCase(),
    amount_ap: amountStr
  };
  return JSON.stringify(payload);
}

function verifySignature(canonicalString, signature, expectedAddress) {
  try {
    const msgHash = ethers.hashMessage(canonicalString);
    const recovered = ethers.recoverAddress(msgHash, signature);
    return recovered.toLowerCase() === String(expectedAddress).toLowerCase();
  } catch (_e) {
    return false;
  }
}

/**
 * Ejecuta permit() + transferFrom() usando datos firmados off-chain
 * @param {Object} permitData - { owner, spender, value, nonce, deadline }
 * @param {Object} signature - { v, r, s }
 * @param {string} seller - Dirección del vendedor
 * @param {string} amount - Cantidad en AP (ej: "100")
 * @returns {Object} { permitTxHash, transferTxHash }
 */
async function settleWithPermit(permitData, signature, seller, amount) {
  try {
    console.log('[SETTLE_PERMIT] 🚀 Iniciando settle con permit...');
    console.log('[SETTLE_PERMIT] Owner:', permitData.owner);
    console.log('[SETTLE_PERMIT] Spender:', permitData.spender);
    console.log('[SETTLE_PERMIT] Value:', permitData.value);
    console.log('[SETTLE_PERMIT] Deadline:', permitData.deadline);
    console.log('[SETTLE_PERMIT] Nonce:', permitData.nonce);
    
    // Verificar deadline
    const now = Math.floor(Date.now() / 1000);
    if (permitData.deadline < now) {
      throw new Error(`Permit expirado. Deadline: ${permitData.deadline}, Now: ${now}`);
    }
    
    // CRÍTICO: Verificar nonce del blockchain antes de ejecutar permit
    console.log('[SETTLE_PERMIT] 🔍 Verificando nonce del blockchain...');
    const blockchainNonce = await tokenContract.nonces(permitData.owner);
    const expectedNonce = BigInt(blockchainNonce);
    const providedNonce = BigInt(permitData.nonce);
    
    console.log(`[SETTLE_PERMIT] Nonce del blockchain: ${expectedNonce.toString()}`);
    console.log(`[SETTLE_PERMIT] Nonce proporcionado: ${providedNonce.toString()}`);
    
    if (providedNonce !== expectedNonce) {
      throw new Error(
        `NONCE_MISMATCH: El nonce proporcionado (${providedNonce}) no coincide con el nonce del blockchain (${expectedNonce}). ` +
        `El usuario debe sincronizar su wallet antes de intentar esta transacción.`
      );
    }
    
    console.log('[SETTLE_PERMIT] ✅ Nonce verificado correctamente');
    
    // IMPORTANTE: permitData.value ya viene en wei desde la app
    // NO usar parseUnits porque multiplicaría por 10^18 de nuevo
    const permitValue = BigInt(permitData.value);
    const transferAmount = ethers.parseUnits(amount, 18);
    
    console.log(`[SETTLE_PERMIT] 📊 Comparando valores:`);
    console.log(`  Permit value: ${permitValue.toString()} wei`);
    console.log(`  Transfer amount: ${transferAmount.toString()} wei`);
    
    if (permitValue < transferAmount) {
      throw new Error(`Permit insuficiente. Permit: ${permitData.value} wei, Transfer: ${amount} AP (${transferAmount} wei)`);
    }
    
    // Convertir deadline y nonce a BigInt (crítico para evitar truncamiento)
    const deadlineBigInt = BigInt(permitData.deadline);
    const nonceBigInt = BigInt(permitData.nonce);
    
    console.log('[SETTLE_PERMIT] 📊 Valores para permit:');
    console.log(`  Owner: ${permitData.owner}`);
    console.log(`  Spender: ${permitData.spender}`);
    console.log(`  Value: ${permitValue.toString()} wei`);
    console.log(`  Deadline: ${deadlineBigInt.toString()}`);
    console.log(`  Nonce: ${nonceBigInt.toString()}`);
    console.log(`  v: ${signature.v}`);
    console.log(`  r: ${signature.r}`);
    console.log(`  s: ${signature.s}`);
    
    // Paso 1: Ejecutar permit()
    console.log('[SETTLE_PERMIT] 📝 Ejecutando permit()...');
    const permitTx = await tokenContract.permit(
      permitData.owner,
      permitData.spender,
      permitValue,
      deadlineBigInt,  // BigInt en lugar de number
      signature.v,
      signature.r,
      signature.s
    );
    
    console.log(`[SETTLE_PERMIT] 🔄 Permit TX enviada: ${permitTx.hash}`);
    console.log(`[SETTLE_PERMIT] ⏳ Esperando ${CONFIRMATIONS} confirmación(es)...`);
    
    const permitReceipt = await permitTx.wait(CONFIRMATIONS);
    console.log(`[SETTLE_PERMIT] ✅ Permit confirmado en bloque: ${permitReceipt.blockNumber}`);
    
    // Paso 2: Ejecutar transferFrom() con MUTEX para evitar conflictos de nonce
    // Esto asegura que múltiples peticiones concurrentes no intenten usar el mismo nonce
    console.log('[SETTLE_PERMIT] 🔒 Esperando lock para transferFrom()...');
    const { transferTx, transferReceipt } = await transferFromMutex.runExclusive(async () => {
      console.log('[SETTLE_PERMIT] 💸 Ejecutando transferFrom()...');
      const tx = await tokenContract.transferFrom(
        permitData.owner,
        seller,
        transferAmount
      );
      
      console.log(`[SETTLE_PERMIT] 🔄 Transfer TX enviada: ${tx.hash}`);
      console.log(`[SETTLE_PERMIT] ⏳ Esperando ${CONFIRMATIONS} confirmación(es)...`);
      
      const receipt = await tx.wait(CONFIRMATIONS);
      console.log(`[SETTLE_PERMIT] ✅ Transfer confirmado en bloque: ${receipt.blockNumber}`);
      
      return { transferTx: tx, transferReceipt: receipt };
    });
    
    console.log('[SETTLE_PERMIT] 🎉 Settle completado exitosamente');
    
    return {
      permitTxHash: permitTx.hash,
      transferTxHash: transferTx.hash
    };
    
  } catch (error) {
    console.error('[SETTLE_PERMIT] ❌ Error:', error.message);
    throw error;
  }
}

// ──────────────────────── Validación de request ───────────────────────
function validateVoucherRequest(req) {
  const { offer_id, amount_ap, buyer_alias, seller_alias, created_at } = req.body;

  if (!offer_id || typeof offer_id !== 'string') {
    return { valid: false, error: 'offer_id es obligatorio y debe ser string' };
  }

  if (typeof amount_ap !== 'number' || !Number.isFinite(amount_ap) || amount_ap <= 0) {
    return { valid: false, error: 'amount_ap debe ser un número mayor a 0' };
  }

  if (amount_ap > 100000) {
    return { valid: false, error: 'amount_ap excede el límite máximo (100,000 AP)' };
  }

  if (!buyer_alias || !seller_alias) {
    return { valid: false, error: 'buyer_alias y seller_alias son obligatorios' };
  }

  const now = Math.floor(Date.now() / 1000);
  if (!created_at || typeof created_at !== 'number' || created_at < now - 3600 || created_at > now + 300) {
    return { valid: false, error: 'created_at debe ser razonable (última hora)' };
  }

  return { valid: true };
}

// ───────────────────────────── Endpoints ──────────────────────────────

// ═════════════════════════════════════════════════════════════════════════════
// NUEVO ENDPOINT - TRUE SELF-CUSTODY MODEL
// ═════════════════════════════════════════════════════════════════════════════

// POST /wallet/register - Registra un nuevo wallet (SOLO datos públicos)
// Backend NUNCA recibe palabras ni clave privada
app.post('/wallet/register', async (req, res) => {
  try {
    const { address, public_key } = req.body || {};
    
    // Validaciones
    if (!address || !public_key) {
      return res.status(400).json({
        error: 'address y public_key son requeridos',
        error_code: 'BAD_REQUEST'
      });
    }
    
    // Validar formato de dirección (0x seguido de 40 caracteres hex)
    if (!address.match(/^0x[0-9a-fA-F]{40}$/)) {
      return res.status(400).json({
        error: 'Formato de address inválido',
        error_code: 'INVALID_ADDRESS'
      });
    }
    
    // Normalizar address a lowercase
    const addressLower = address.toLowerCase();
    
    // Verificar que no exista ya
    db.get('SELECT * FROM users WHERE address = ?', [addressLower], (err, row) => {
      if (err) {
        console.error('Error consultando usuario:', err);
        return res.status(500).json({
          error: 'Error interno',
          error_code: 'DB_ERROR'
        });
      }
      
      if (row) {
        return res.status(409).json({
          error: 'Wallet ya existe',
          error_code: 'WALLET_EXISTS'
        });
      }
      
      // Generar session token
      const sessionToken = generateSessionToken();
      const now = Math.floor(Date.now() / 1000);
      const expiresAt = now + (7 * 24 * 60 * 60); // 7 días
      
      // Guardar usuario (SOLO datos públicos)
      db.run(`
        INSERT INTO users (address, public_key, session_token, session_expires_at, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?)
      `, [addressLower, public_key, sessionToken, expiresAt, now, now], async function(err) {
        if (err) {
          console.error('Error guardando usuario:', err);
          return res.status(500).json({
            error: 'Error interno',
            error_code: 'DB_ERROR'
          });
        }
        
        console.log(`✅ Nuevo wallet registrado: ${addressLower}`);
        
        // 🎁 FAUCET AUTOMÁTICO: Enviar 1000 AP al nuevo usuario
        // Esto se hace de forma asíncrona (no bloquea la respuesta)
        (async () => {
          try {
            const FAUCET_AMOUNT = '1000'; // Cantidad de AP a enviar
            const decimals = await getDecimals();
            const amountWei = ethers.parseUnits(FAUCET_AMOUNT, decimals);
            
            console.log(`💰 [FAUCET] Enviando ${FAUCET_AMOUNT} AP a ${addressLower}...`);
            
            const tx = await tokenContract.transfer(addressLower, amountWei);
            console.log(`💰 [FAUCET] Transacción enviada: ${tx.hash}`);
            
            // Esperar confirmación en background (no bloquear)
            tx.wait(1).then((receipt) => {
              if (receipt.status === 1) {
                console.log(`✅ [FAUCET] ${FAUCET_AMOUNT} AP enviados exitosamente a ${addressLower}`);
              } else {
                console.error(`❌ [FAUCET] Transacción falló para ${addressLower}`);
              }
            }).catch((waitErr) => {
              console.error(`❌ [FAUCET] Error esperando confirmación para ${addressLower}:`, waitErr.message);
            });
          } catch (faucetError) {
            // Si falla el faucet, solo logear el error (no afecta el registro)
            console.error(`❌ [FAUCET] Error enviando tokens a ${addressLower}:`, faucetError.message);
          }
        })();
        
        // Responder inmediatamente (no esperar el faucet)
        res.status(201).json({
          success: true,
          session_token: sessionToken,
          address: addressLower
        });
      });
    });
  } catch (error) {
    console.error('Error en POST /wallet/register:', error);
    res.status(500).json({
      error: 'Error interno del servidor',
      error_code: 'INTERNAL_ERROR'
    });
  }
});

// GET /wallet/info?address=0x... - Obtiene información de un wallet (para restauración)
app.get('/wallet/info', async (req, res) => {
  try {
    const { address } = req.query;
    
    if (!address) {
      return res.status(400).json({
        error: 'address es requerido',
        error_code: 'BAD_REQUEST'
      });
    }
    
    const addressLower = address.toLowerCase();
    
    db.get('SELECT address, public_key, created_at FROM users WHERE address = ?', 
      [addressLower], (err, row) => {
        if (err) {
          console.error('Error consultando usuario:', err);
          return res.status(500).json({
            error: 'Error interno',
            error_code: 'DB_ERROR'
          });
        }
        
        if (!row) {
          return res.status(404).json({
            error: 'Wallet no encontrado',
            error_code: 'NOT_FOUND'
          });
        }
        
        res.status(200).json({
          address: row.address,
          public_key: row.public_key,
          created_at: row.created_at
        });
      }
    );
  } catch (error) {
    console.error('Error en GET /wallet/info:', error);
    res.status(500).json({
      error: 'Error interno del servidor',
      error_code: 'INTERNAL_ERROR'
    });
  }
});

// POST /wallet/login - Login con dirección (para restauración)
app.post('/wallet/login', async (req, res) => {
  try {
    const { address } = req.body || {};
    
    if (!address) {
      return res.status(400).json({
        error: 'address es requerido',
        error_code: 'BAD_REQUEST'
      });
    }
    
    const addressLower = address.toLowerCase();
    
    db.get('SELECT * FROM users WHERE address = ?', [addressLower], (err, row) => {
      if (err) {
        console.error('Error consultando usuario:', err);
        return res.status(500).json({
          error: 'Error interno',
          error_code: 'DB_ERROR'
        });
      }
      
      if (!row) {
        return res.status(404).json({
          error: 'Wallet no encontrado',
          error_code: 'NOT_FOUND'
        });
      }
      
      // Generar nuevo session token
      const sessionToken = generateSessionToken();
      const now = Math.floor(Date.now() / 1000);
      const expiresAt = now + (7 * 24 * 60 * 60); // 7 días
      
      // Actualizar token
      db.run(
        'UPDATE users SET session_token = ?, session_expires_at = ?, updated_at = ? WHERE address = ?',
        [sessionToken, expiresAt, now, addressLower],
        (err) => {
          if (err) {
            console.error('Error actualizando session:', err);
            return res.status(500).json({
              error: 'Error interno',
              error_code: 'DB_ERROR'
            });
          }
          
          console.log(`✅ Login exitoso: ${addressLower}`);
          
          res.status(200).json({
            session_token: sessionToken,
            address: addressLower
          });
        }
      );
    });
  } catch (error) {
    console.error('Error en POST /wallet/login:', error);
    res.status(500).json({
      error: 'Error interno del servidor',
      error_code: 'INTERNAL_ERROR'
    });
  }
});

// ═════════════════════════════════════════════════════════════════════════════
// ENDPOINTS ANTIGUOS - DEPRECATED (ELIMINAR DESPUÉS DE MIGRACIÓN)
// ═════════════════════════════════════════════════════════════════════════════

// DEPRECATED: POST /wallet/create
// Backend YA NO debe generar wallets - La app genera todo localmente
app.post('/wallet/create', async (req, res) => {
  console.warn('⚠️ DEPRECATED: /wallet/create - Backend no debe generar wallets');
  res.status(410).json({
    error: 'Endpoint deprecado - Usar /wallet/register',
    error_code: 'DEPRECATED_ENDPOINT'
  });
});

// DEPRECATED: POST /auth/login-via-phrase
// Backend YA NO debe recibir frases - La app deriva todo localmente
app.post('/auth/login-via-phrase', async (req, res) => {
  console.warn('⚠️ DEPRECATED: /auth/login-via-phrase - Backend no debe recibir frases');
  res.status(410).json({
    error: 'Endpoint deprecado - Usar /wallet/login con address',
    error_code: 'DEPRECATED_ENDPOINT'
  });
});

// DEPRECATED: GET /wallet/private-key
// ⚠️ PELIGROSO: Backend NUNCA debe enviar claves privadas
app.get('/wallet/private-key', async (req, res) => {
  console.error('❌ DEPRECATED & DANGEROUS: /wallet/private-key - Backend NO debe enviar claves privadas');
  res.status(410).json({
    error: 'Endpoint deprecado y peligroso - ELIMINADO por seguridad',
    error_code: 'DEPRECATED_ENDPOINT'
  });
});

// DEPRECATED: POST /wallet/identity-debug
// ⚠️ MUY PELIGROSO: Endpoint que expone claves privadas
app.post('/wallet/identity-debug', async (req, res) => {
  console.error('❌ DEPRECATED & VERY DANGEROUS: /wallet/identity-debug - Endpoint eliminado por seguridad');
  res.status(410).json({
    error: 'Endpoint deprecado y MUY PELIGROSO - ELIMINADO permanentemente',
    error_code: 'DEPRECATED_ENDPOINT'
  });
});

// POST /v1/vouchers
app.post('/v1/vouchers', async (req, res) => {
  try {
    const validation = validateVoucherRequest(req);
    if (!validation.valid) {
      return res.status(400).json({
        error: validation.error,
        error_code: 'VALIDATION_ERROR'
      });
    }

    const { offer_id, amount_ap, buyer_alias, seller_alias, created_at } = req.body;

    // Idempotencia por offer_id
    db.get('SELECT offer_id, tx_hash, status FROM vouchers WHERE offer_id = ?', [offer_id], (err, row) => {
      if (err) {
        console.error('Error consultando DB:', err);
        return res.status(500).json({ error: 'Error interno', error_code: 'DB_ERROR' });
      }
      if (row) {
        if (row.tx_hash) {
          return res.status(200).json({ offer_id, tx_hash: row.tx_hash, status: 'SUBIDO_OK' });
        }
        return res.status(409).json({ error: 'offer_id duplicado', error_code: 'DUPLICATE_OFFER_ID' });
      }

      // Resolver destinatario (0x directo o alias)
      let sellerAddress;
      try {
        sellerAddress = resolveAddress(seller_alias);
      } catch {
        return res.status(400).json({
          error: `Alias o dirección inválida: ${seller_alias}`,
          error_code: 'ALIAS_NOT_FOUND'
        });
      }

      (async () => {
        try {
          const decimals = await getDecimals();
          const amountWei = ethers.parseUnits(amount_ap.toString(), decimals);

          // Insert PENDING
          const now = Math.floor(Date.now() / 1000);
          db.run(
            'INSERT INTO vouchers (offer_id, amount_ap, buyer_alias, seller_alias, tx_hash, status, onchain_status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)',
            [offer_id, amount_ap, buyer_alias, seller_alias, null, 'PENDING', 'PENDING', created_at, now],
            async (insErr) => {
              if (insErr) {
                console.error('Error insertando voucher:', insErr);
                return res.status(500).json({ error: 'Error guardando voucher', error_code: 'DB_ERROR' });
              }

              try {
                // Transferencia on-chain
                const tx = await tokenContract.transfer(sellerAddress, amountWei);
                console.log(`Transacción enviada: ${tx.hash}`);

                // Guardar tx_hash
                db.run(
                  'UPDATE vouchers SET tx_hash = ?, status = ?, onchain_status = ?, updated_at = ? WHERE offer_id = ?',
                  [tx.hash, 'PENDING', 'PENDING', now, offer_id],
                  (upErr) => { if (upErr) console.error('Error actualizando tx_hash:', upErr); }
                );

                // Esperar confirmación(es)
                const receipt = await tx.wait(CONFIRMATIONS);
                const finalStatus = receipt.status === 1 ? 'SUBIDO_OK' : 'FAILED';
                const finalOnchainStatus = receipt.status === 1 ? 'CONFIRMED' : 'FAILED';

                db.run(
                  'UPDATE vouchers SET status = ?, onchain_status = ?, updated_at = ? WHERE offer_id = ?',
                  [finalStatus, finalOnchainStatus, Math.floor(Date.now() / 1000), offer_id],
                  (finErr) => { if (finErr) console.error('Error actualizando estado final:', finErr); }
                );

                // Responder con tx_hash
                return res.status(200).json({ offer_id, tx_hash: tx.hash, status: 'SUBIDO_OK' });
              } catch (txError) {
                console.error('Error en transacción:', txError);
                db.run(
                  'UPDATE vouchers SET status = ?, onchain_status = ?, updated_at = ? WHERE offer_id = ?',
                  ['ERROR', 'FAILED', Math.floor(Date.now() / 1000), offer_id],
                  () => {}
                );
                return res.status(500).json({
                  error: `Error ejecutando transacción: ${txError.message}`,
                  error_code: 'TX_ERROR'
                });
              }
            }
          );
        } catch (prepErr) {
          console.error('Error preparando transferencia:', prepErr);
          return res.status(500).json({ error: 'Error interno', error_code: 'INTERNAL_ERROR' });
        }
      })();
    });
  } catch (error) {
    console.error('Error en POST /v1/vouchers:', error);
    res.status(500).json({ error: 'Error interno del servidor', error_code: 'INTERNAL_ERROR' });
  }
});

// POST /v1/vouchers/settle (con rate limiting estricto)
app.post('/v1/vouchers/settle', settleLimiter, async (req, res) => {
  try {
    const {
      offer_id, amount_ap, asset, expiry,
      seller_address, seller_sig,
      buyer_address, buyer_sig,
      canonical,
      permit,        // NUEVO: Datos del permit
      permit_sig     // NUEVO: Firma del permit
    } = req.body || {};

    console.log(`\n${'='.repeat(80)}`);
    console.log(`[SETTLE] 📦 Nueva solicitud de settle con PERMIT`);
    console.log(`[SETTLE] Offer ID: ${offer_id}`);
    console.log(`[SETTLE] Buyer: ${buyer_address}`);
    console.log(`[SETTLE] Seller: ${seller_address}`);
    console.log(`[SETTLE] Amount: ${amount_ap} AP`);
    console.log(`${'='.repeat(80)}\n`);

    // ═══════════════════════════════════════════════════════════════════
    // VALIDACIONES BÁSICAS
    // ═══════════════════════════════════════════════════════════════════
    
    if (!offer_id || !amount_ap || !asset || !expiry || !seller_address || !seller_sig || !buyer_address || !buyer_sig) {
      console.warn(`🔒 [SETTLE] Campos faltantes en request`);
      return res.status(400).json({ error_code: 'BAD_REQUEST', message: 'Missing fields' });
    }
    
    // NUEVO: Validar campos de permit
    if (!permit || !permit_sig) {
      console.log('[SETTLE] ❌ Faltan datos de permit');
      return res.status(400).json({
        error_code: 'MISSING_PERMIT',
        message: 'Se requieren datos de permit (permit y permit_sig)'
      });
    }
    
    if (!permit.owner || !permit.spender || !permit.value || !permit.deadline || permit.nonce === undefined) {
      console.log('[SETTLE] ❌ Datos de permit incompletos');
      return res.status(400).json({
        error_code: 'INVALID_PERMIT_DATA',
        message: 'Datos de permit incompletos (requiere: owner, spender, value, nonce, deadline)'
      });
    }
    
    if (!permit_sig.v || !permit_sig.r || !permit_sig.s) {
      console.log('[SETTLE] ❌ Firma de permit inválida');
      return res.status(400).json({
        error_code: 'INVALID_PERMIT_SIGNATURE',
        message: 'Firma de permit inválida (requiere: v, r, s)'
      });
    }
    
    if (!/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(String(offer_id))) {
      console.warn(`🔒 [SETTLE] offer_id inválido: ${offer_id}`);
      return res.status(400).json({ error_code: 'BAD_OFFER_ID', message: 'offer_id must be UUID v4' });
    }
    
    if (String(asset) !== 'AP') {
      console.warn(`🔒 [SETTLE] asset inválido: ${asset}`);
      return res.status(400).json({ error_code: 'BAD_ASSET', message: 'asset must be "AP"' });
    }
    
    const now = Math.floor(Date.now() / 1000);
    if (Number(expiry) <= now) {
      console.warn(`🔒 [SETTLE] Voucher expirado: ${offer_id}, expiry: ${expiry}, now: ${now}`);
      return res.status(409).json({ error_code: 'EXPIRED', message: 'Voucher expired' });
    }

    if (!isHexAddress(seller_address) || !isHexAddress(buyer_address)) {
      console.warn(`🔒 [SETTLE] Dirección inválida - seller: ${seller_address}, buyer: ${buyer_address}`);
      return res.status(400).json({ error_code: 'BAD_ADDRESS', message: 'seller_address y buyer_address deben ser 0x...' });
    }

    const amountNumeric = Number(amount_ap);
    if (!Number.isFinite(amountNumeric) || amountNumeric <= 0) {
      console.warn(`🔒 [SETTLE] Monto inválido: ${amount_ap}`);
      return res.status(400).json({ error_code: 'BAD_AMOUNT', message: 'amount_ap inválido' });
    }

    const sellerLower = seller_address.toLowerCase();
    const buyerLower = buyer_address.toLowerCase();

    // ═══════════════════════════════════════════════════════════════════
    // SEGURIDAD CRÍTICA: Validar que buyer ≠ seller
    // ═══════════════════════════════════════════════════════════════════
    
    if (buyerLower === sellerLower) {
      console.error(`🚨 [SETTLE] ATAQUE DETECTADO: Auto-transferencia - ${buyerLower}`);
      return res.status(400).json({ 
        error_code: 'SAME_ADDRESS', 
        message: 'buyer y seller no pueden ser la misma dirección' 
      });
    }

    // ═══════════════════════════════════════════════════════════════════
    // SEGURIDAD CRÍTICA: Verificar que las addresses estén registradas
    // ═══════════════════════════════════════════════════════════════════
    
    const [buyerExists, sellerExists] = await Promise.all([
      new Promise((resolve) => {
        db.get('SELECT address FROM users WHERE address = ?', [buyerLower], (err, row) => {
          resolve(!err && !!row);
        });
      }),
      new Promise((resolve) => {
        db.get('SELECT address FROM users WHERE address = ?', [sellerLower], (err, row) => {
          resolve(!err && !!row);
        });
      })
    ]);

    if (!buyerExists) {
      console.error(`🚨 [SETTLE] ATAQUE DETECTADO: Buyer no registrado - ${buyerLower}`);
      return res.status(403).json({ 
        error_code: 'BUYER_NOT_REGISTERED', 
        message: 'Buyer address no está registrado en el sistema' 
      });
    }

    if (!sellerExists) {
      console.error(`🚨 [SETTLE] ATAQUE DETECTADO: Seller no registrado - ${sellerLower}`);
      return res.status(403).json({ 
        error_code: 'SELLER_NOT_REGISTERED', 
        message: 'Seller address no está registrado en el sistema' 
      });
    }

    // ═══════════════════════════════════════════════════════════════════
    // CANONICALIZACIÓN Y VERIFICACIÓN DE FIRMAS
    // ═══════════════════════════════════════════════════════════════════
    
    // Si canonical viene en el body, usarlo; si no, calcularlo
    let canonicalMsg = canonical;
    if (!canonicalMsg) {
      const base = {
        offer_id,
        amount_ap: String(amount_ap),
        asset,
        expiry: Number(expiry),
        seller_address: sellerLower,
        buyer_address: buyerLower
      };
      
      try {
        canonicalMsg = canonicalizePaymentBase(base);
      } catch (e) {
        console.error(`🔒 [SETTLE] Error canonicalizando: ${e.message}`);
        return res.status(400).json({ error_code: 'BAD_CANONICAL', message: String(e.message) });
      }
    }

    console.log(`🔒 [SETTLE] Verificando firmas para ${offer_id}...`);
    console.log(`   Buyer: ${buyerLower}`);
    console.log(`   Seller: ${sellerLower}`);
    console.log(`   Amount: ${amount_ap} AP`);

    // Verificar firma del seller
    const okSeller = verifySignature(canonicalMsg, seller_sig, sellerLower);
    if (!okSeller) {
      console.error(`🚨 [SETTLE] FIRMA INVÁLIDA: Seller signature falló - ${sellerLower}`);
      console.error(`   offer_id: ${offer_id}`);
      console.error(`   canonical: ${canonicalMsg}`);
      console.error(`   seller_sig: ${seller_sig}`);
      return res.status(422).json({ 
        error_code: 'INVALID_SELLER_SIGNATURE', 
        message: 'Firma del vendedor inválida' 
      });
    }

    // Verificar firma del buyer
    const okBuyer = verifySignature(canonicalMsg, buyer_sig, buyerLower);
    if (!okBuyer) {
      console.error(`🚨 [SETTLE] FIRMA INVÁLIDA: Buyer signature falló - ${buyerLower}`);
      console.error(`   offer_id: ${offer_id}`);
      console.error(`   canonical: ${canonicalMsg}`);
      console.error(`   buyer_sig: ${buyer_sig}`);
      return res.status(422).json({ 
        error_code: 'INVALID_BUYER_SIGNATURE', 
        message: 'Firma del comprador inválida' 
      });
    }

    console.log(`✅ [SETTLE] Firmas del voucher verificadas exitosamente para ${offer_id}`);

    // ═══════════════════════════════════════════════════════════════════
    // IDEMPOTENCIA: Verificar si el voucher ya existe O está en proceso
    // ═══════════════════════════════════════════════════════════════════
    
    const existing = await new Promise((resolve, reject) => {
      db.get(
        'SELECT offer_id, permit_tx_hash, transfer_tx_hash, status FROM vouchers WHERE offer_id = ?',
        [offer_id],
        (err, row) => {
          if (err) reject(err);
          else resolve(row);
        }
      );
    });
    
    if (existing) {
      // Si tiene transfer_tx_hash, ya está completado
      if (existing.transfer_tx_hash) {
        console.log(`[SETTLE] ⚠️  Voucher ya procesado: ${offer_id}`);
        console.log(`[SETTLE]    Transfer TX: ${existing.transfer_tx_hash}`);
        return res.json({
          status: 'already_settled',
          offer_id: offer_id,
          message: 'Voucher ya fue procesado anteriormente (idempotencia)',
          permit_tx_hash: existing.permit_tx_hash,
          transfer_tx_hash: existing.transfer_tx_hash
        });
      }
      
      // Si está marcado como PROCESSING, es un duplicado en progreso
      if (existing.status === 'PROCESSING') {
        console.log(`[SETTLE] ⚠️  Voucher ya está siendo procesado por otra petición: ${offer_id}`);
        // Esperar un poco y reintentar la consulta
        await new Promise(resolve => setTimeout(resolve, 2000));
        
        const updated = await new Promise((resolve, reject) => {
          db.get(
            'SELECT offer_id, permit_tx_hash, transfer_tx_hash FROM vouchers WHERE offer_id = ?',
            [offer_id],
            (err, row) => {
              if (err) reject(err);
              else resolve(row);
            }
          );
        });
        
        if (updated && updated.transfer_tx_hash) {
          console.log(`[SETTLE] ✅ Procesamiento completado por otra petición: ${offer_id}`);
          return res.json({
            status: 'already_settled',
            offer_id: offer_id,
            message: 'Procesado por petición paralela',
            permit_tx_hash: updated.permit_tx_hash,
            transfer_tx_hash: updated.transfer_tx_hash
          });
        } else {
          console.log(`[SETTLE] ⚠️  Procesamiento aún en curso, abortando duplicado: ${offer_id}`);
          return res.status(409).json({
            error_code: 'PROCESSING_IN_PROGRESS',
            message: 'Voucher está siendo procesado por otra petición'
          });
        }
      }
    }
    
    // ═══════════════════════════════════════════════════════════════════
    // BLOQUEO OPTIMISTA: Marcar como "PROCESSING" para evitar duplicados
    // ═══════════════════════════════════════════════════════════════════
    
    const lockAcquired = await new Promise((resolve, reject) => {
      const nowMs = Math.floor(Date.now() / 1000);
      db.run(
        `INSERT OR IGNORE INTO vouchers (
          offer_id, buyer_address, seller_address, amount_ap_str,
          asset, expiry, status, created_at, updated_at,
          buyer_sig, seller_sig, payload_canonical,
          buyer_alias, seller_alias, amount_ap
        ) VALUES (?, ?, ?, ?, ?, ?, 'PROCESSING', ?, ?, ?, ?, ?, ?, ?, ?)`,
        [
          offer_id, buyerLower, sellerLower, String(amount_ap),
          asset, expiry, nowMs, nowMs,
          buyer_sig, seller_sig, canonicalMsg,
          buyerLower.substring(0, 10), sellerLower.substring(0, 10),
          amount_ap
        ],
        function(err) {
          if (err) {
            console.error(`[SETTLE] ❌ Error adquiriendo bloqueo: ${err.message}`);
            reject(err);
          } else {
            // Si changes === 0, significa que otro proceso ya insertó (UNIQUE constraint)
            resolve(this.changes > 0);
          }
        }
      );
    });
    
    if (!lockAcquired) {
      console.log(`[SETTLE] ⚠️  Otro proceso ya adquirió el bloqueo para: ${offer_id}`);
      // Esperar más tiempo para que la primera petición complete (máximo 15 segundos)
      let attempts = 0;
      const maxAttempts = 5; // 5 intentos × 3 segundos = 15 segundos máximo
      
      while (attempts < maxAttempts) {
        await new Promise(resolve => setTimeout(resolve, 3000)); // Esperar 3 segundos
        
        const result = await new Promise((resolve, reject) => {
          db.get(
            'SELECT offer_id, permit_tx_hash, transfer_tx_hash, status FROM vouchers WHERE offer_id = ?',
            [offer_id],
            (err, row) => {
              if (err) reject(err);
              else resolve(row);
            }
          );
        });
        
        if (result && result.transfer_tx_hash) {
          console.log(`[SETTLE] ✅ Primera petición completó el procesamiento de: ${offer_id}`);
          return res.json({
            status: 'already_settled',
            offer_id: offer_id,
            message: 'Procesado por petición paralela',
            permit_tx_hash: result.permit_tx_hash,
            transfer_tx_hash: result.transfer_tx_hash
          });
        } else if (result && result.status === 'FAILED') {
          console.log(`[SETTLE] ❌ Primera petición falló para: ${offer_id}`);
          return res.status(500).json({
            error_code: 'PROCESSING_FAILED',
            message: 'La primera petición falló, reintente'
          });
        }
        
        attempts++;
        console.log(`[SETTLE] ⏳ Esperando... intento ${attempts}/${maxAttempts}`);
      }
      
      // Si después de 15 segundos aún no se completó, retornar error
      return res.status(409).json({
        error_code: 'PROCESSING_TIMEOUT',
        message: 'Voucher está siendo procesado, reintente más tarde'
      });
    }
    
    console.log(`[SETTLE] 🔒 Bloqueo adquirido para: ${offer_id}`);

    // ═══════════════════════════════════════════════════════════════════
    // EJECUTAR PERMIT + TRANSFERFROM (con mutex por usuario)
    // ═══════════════════════════════════════════════════════════════════
    
    console.log('[SETTLE] 🚀 Ejecutando settle con permit...');
    
    // Obtener mutex del usuario para serializar transacciones del mismo comprador
    // Esto asegura que transacciones offline consecutivas (nonce 0, 1, 2...) se procesen en orden
    const buyerAddress = permit.owner;
    const userMutex = getUserMutex(buyerAddress);
    
    console.log(`[SETTLE] 🔒 Esperando mutex del usuario ${buyerAddress}...`);
    const result = await userMutex.runExclusive(async () => {
      console.log(`[SETTLE] ✅ Mutex adquirido para usuario ${buyerAddress}`);
      return await settleWithPermit(permit, permit_sig, sellerLower, String(amount_ap));
    });
    console.log(`[SETTLE] 🔓 Mutex liberado para usuario ${buyerAddress}`);
    
    console.log(`[SETTLE] ✅ Permit TX: ${result.permitTxHash}`);
    console.log(`[SETTLE] ✅ Transfer TX: ${result.transferTxHash}`);
    
    // ═══════════════════════════════════════════════════════════════════
    // GUARDAR EN DB
    // ═══════════════════════════════════════════════════════════════════
    
    const nowMs = Date.now();
    
    await new Promise((resolve, reject) => {
      db.run(
        `INSERT OR REPLACE INTO vouchers (
          offer_id, buyer_address, seller_address, amount_ap, status, 
          buyer_sig, seller_sig, payload_canonical, expiry, 
          permit_tx_hash, transfer_tx_hash, tx_hash, created_at, updated_at,
          asset, amount_ap_str, onchain_status,
          buyer_alias, seller_alias
        )
        VALUES (?, ?, ?, ?, 'SETTLED', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'CONFIRMED', ?, ?)`,
        [
          offer_id,
          buyerLower,
          sellerLower,
          Math.floor(amountNumeric),
          buyer_sig,
          seller_sig,
          canonicalMsg || '',
          Number(expiry),
          result.permitTxHash,
          result.transferTxHash,
          result.transferTxHash, // tx_hash apunta al transfer para compatibilidad
          nowMs,
          nowMs,
          'AP',
          String(amount_ap),
          // Usar primeros caracteres de address como alias (para compatibilidad con schema)
          buyerLower.substring(0, 10),
          sellerLower.substring(0, 10)
        ],
        (err) => {
          if (err) reject(err);
          else resolve();
        }
      );
    });
    
    console.log('[SETTLE] ✅ Voucher guardado en DB');
    console.log(`[SETTLE] 🎉 SETTLE COMPLETADO EXITOSAMENTE\n`);
    
    res.json({
      status: 'queued',  // ← CRÍTICO: Campo 'status' que la app espera
      offer_id: offer_id,
      permit_tx_hash: result.permitTxHash,
      transfer_tx_hash: result.transferTxHash,
      buyer_address: buyerLower,
      seller_address: sellerLower,
      amount_ap: String(amount_ap)
    });
    
  } catch (e) {
    console.error('[SETTLE] ❌ Error fatal:', e);
    
    // Obtener offer_id de forma segura (puede no estar definido si el error fue muy temprano)
    const safeOfferId = req.body?.offer_id;
    
    // Limpiar el bloqueo si falla (marcar como FAILED en lugar de PROCESSING)
    if (safeOfferId) {
      try {
        await new Promise((resolve) => {
          db.run(
            'UPDATE vouchers SET status = ?, updated_at = ? WHERE offer_id = ? AND status = ?',
            ['FAILED', Math.floor(Date.now() / 1000), safeOfferId, 'PROCESSING'],
            () => resolve()
          );
        });
        console.log(`[SETTLE] 🧹 Bloqueo limpiado para: ${safeOfferId}`);
      } catch (cleanupErr) {
        console.error('[SETTLE] ❌ Error limpiando bloqueo:', cleanupErr);
      }
    }
    
    res.status(500).json({
      error_code: 'SETTLE_FAILED',
      message: e.message || 'Error al procesar settle'
    });
  }
});

// GET /v1/tx/{offer_id}
app.get('/v1/tx/:offer_id', async (req, res) => {
  try {
    const { offer_id } = req.params;

    db.get('SELECT tx_hash, onchain_status FROM vouchers WHERE offer_id = ?', [offer_id], async (err, row) => {
      if (err) {
        return res.status(500).json({ error: 'Error consultando DB', error_code: 'DB_ERROR' });
      }

      if (!row) {
        return res.status(404).json({ error: 'Transacción no encontrada', error_code: 'NOT_FOUND' });
      }

      if (row.tx_hash) {
        try {
          const receipt = await provider.getTransactionReceipt(row.tx_hash);
          const onchainStatus = receipt ? (receipt.status === 1 ? 'CONFIRMED' : 'FAILED') : 'PENDING';

          db.run(
            'UPDATE vouchers SET onchain_status = ?, updated_at = ? WHERE offer_id = ?',
            [onchainStatus, Math.floor(Date.now() / 1000), offer_id],
            () => {}
          );

          return res.status(200).json({ offer_id, tx_hash: row.tx_hash, onchain_status: onchainStatus });
        } catch {
          // Si aún no hay receipt: devuelve lo último conocido
          return res.status(200).json({
            offer_id,
            tx_hash: row.tx_hash,
            onchain_status: row.onchain_status || 'PENDING'
          });
        }
      }

      return res.status(200).json({ offer_id, tx_hash: null, onchain_status: 'PENDING' });
    });
  } catch (error) {
    console.error('Error en GET /v1/tx:', error);
    res.status(500).json({ error: 'Error interno del servidor', error_code: 'INTERNAL_ERROR' });
  }
});

// GET /v1/balance/{alias}
app.get('/v1/balance/:alias', async (req, res) => {
  try {
    const { alias } = req.params;

    let address = null;
    if (isHexAddress(alias)) {
      address = alias;                 // permite /v1/balance/0x...
    } else {
      address = getAddressFromAlias(alias);
    }

    if (!address) {
      return res.status(404).json({
        error: `Alias o dirección no encontrado(a): ${alias}`,
        error_code: 'ALIAS_NOT_FOUND'
      });
    }

    try {
      const balance = await tokenContract.balanceOf(address);
      const decimals = await getDecimals();
      const balanceFormatted = ethers.formatUnits(balance, decimals);

      res.status(200).json({
        alias,
        balance_ap: Math.floor(parseFloat(balanceFormatted))
      });
    } catch (balanceError) {
      res.status(500).json({
        error: `Error consultando balance: ${balanceError.message}`,
        error_code: 'BALANCE_ERROR'
      });
    }
  } catch (error) {
    console.error('Error en GET /v1/balance:', error);
    res.status(500).json({ error: 'Error interno del servidor', error_code: 'INTERNAL_ERROR' });
  }
});

// GET /v1/wallet/balance?address=0xXXXX
app.get('/v1/wallet/balance', async (req, res) => {
  try {
    const address = req.query.address;

    // Validar que se proporcionó el address
    if (!address) {
      return res.status(400).json({
        error: 'Parámetro "address" requerido',
        error_code: 'ADDRESS_REQUIRED'
      });
    }

    // Validar formato del address
    if (!isHexAddress(address)) {
      return res.status(400).json({
        error: 'Dirección inválida. Debe ser una dirección hexadecimal válida (0x...)',
        error_code: 'INVALID_ADDRESS'
      });
    }

    try {
      // Llamar al contrato AgroPuntos: balanceOf(address)
      const balance = await tokenContract.balanceOf(address);
      const decimals = await getDecimals();
      const balanceFormatted = ethers.formatUnits(balance, decimals);

      // Devolver balance en decimales
      res.status(200).json({
        balance_ap: Math.floor(parseFloat(balanceFormatted))
      });
    } catch (rpcError) {
      // Manejar errores RPC (conexión, contrato, etc.)
      console.error('Error RPC consultando balance:', rpcError);
      res.status(500).json({
        error: `Error consultando balance en la blockchain: ${rpcError.message}`,
        error_code: 'RPC_ERROR'
      });
    }
  } catch (error) {
    console.error('Error en GET /v1/wallet/balance:', error);
    res.status(500).json({
      error: 'Error interno del servidor',
      error_code: 'INTERNAL_ERROR'
    });
  }
});

async function processOutboxOnce() {
  db.all(`
          SELECT o.offer_id
          FROM outbox o
          WHERE (o.state='PENDING'
             OR (o.state='FAILED' AND (strftime('%s','now') - o.updated_at) >= 60)
             OR (o.state='PROCESSING' AND (strftime('%s','now') - o.updated_at) >= 300))
          LIMIT 10
        `, [], async (err, pendings) => {
    if (err || !pendings || pendings.length === 0) return;

    for (const row of pendings) {
      const offer_id = row.offer_id;
      
      // Marcar como PROCESSING inmediatamente para evitar procesamiento duplicado
      // Usar una actualización atómica que solo funciona si el estado es PENDING, FAILED o PROCESSING (stale)
      db.run(
        `UPDATE outbox SET state='PROCESSING', updated_at=strftime('%s','now') 
         WHERE offer_id=? AND (
           state='PENDING' 
           OR (state='FAILED' AND (strftime('%s','now') - updated_at) >= 60)
           OR (state='PROCESSING' AND (strftime('%s','now') - updated_at) >= 300)
         )`,
        [offer_id],
        function(updateErr) {
          if (updateErr) {
            console.error(`[OUTBOX] Error marcando PROCESSING para ${offer_id}:`, updateErr);
            return;
          }
          
          // Si no se actualizó ninguna fila, significa que otro proceso ya lo está procesando
          if (this.changes === 0) {
            return;
          }

          // Ahora procesar el voucher
          db.get(`SELECT * FROM vouchers WHERE offer_id=?`, [offer_id], async (e, v) => {
            if (e || !v) {
              markOutbox(offer_id, 'FAILED', 'VOUCHER_NOT_FOUND', () => {});
              return;
            }

            // Verificar si ya existe una transacción (incluso si está pendiente)
            if (v.tx_hash) {
              // Si ya tiene tx_hash, verificar el estado on-chain
              if (v.status === 'SUBIDO_OK' || v.onchain_status === 'CONFIRMED') {
                markOutbox(offer_id, 'SENT', null, () => {});
                return;
              }
              // Si tiene tx_hash pero está pendiente, no crear otra transacción
              // Solo marcar como SENT si se confirma, o dejar PROCESSING para reintentar
              console.log(`[OUTBOX] offer_id=${offer_id} ya tiene tx_hash=${v.tx_hash}, esperando confirmación`);
              return;
            }

            try {
              // --- dentro de processOutboxOnce(), justo antes de firmar la tx ---
              console.log(`[OUTBOX] Procesando voucher: ${offer_id}`);
              const decimals = await getDecimals();

              const amountStr = v.amount_ap_str;               // viene de /settle
              if (!amountStr) {
                console.error(`[OUTBOX] ❌ ${offer_id}: MISSING_amount_ap_str`);
                markOutbox(offer_id, 'FAILED', 'MISSING_amount_ap_str', () => {});
                return;
              }
              const requested = ethers.parseUnits(String(amountStr), decimals);

              const from = v.buyer_address;                    // A
              const to   = v.seller_address;                   // B

              if (!/^0x[a-fA-F0-9]{40}$/.test(from)) {
                console.error(`[OUTBOX] ❌ ${offer_id}: BAD_BUYER_ADDRESS: ${from}`);
                markOutbox(offer_id, 'FAILED', 'BAD_BUYER_ADDRESS', () => {});
                return;
              }
              if (!/^0x[a-fA-F0-9]{40}$/.test(to)) {
                console.error(`[OUTBOX] ❌ ${offer_id}: BAD_SELLER_ADDRESS: ${to}`);
                markOutbox(offer_id, 'FAILED', 'BAD_SELLER_ADDRESS', () => {});
                return;
              }

              console.log(`[OUTBOX] Verificando balance y allowance para ${offer_id}`);
              console.log(`  Contrato: ${CONTRACT_ADDRESS}`);
              console.log(`  Buyer (from): ${from}`);
              console.log(`  Seller (to): ${to}`);
              console.log(`  Cuenta madre (spender): ${wallet.address}`);
              console.log(`  Monto solicitado: ${amountStr} AP (${requested.toString()} wei)`);

              // Chequear balance y allowance de A
              const [balanceA, allowanceAB] = await Promise.all([
                tokenContract.balanceOf(from),
                tokenContract.allowance(from, wallet.address)   // madre como spender
              ]);

              console.log(`[OUTBOX] Balance de ${from}: ${balanceA.toString()} wei (${ethers.formatUnits(balanceA, decimals)} AP)`);
              console.log(`[OUTBOX] Allowance de ${from} hacia ${wallet.address}: ${allowanceAB.toString()} wei (${ethers.formatUnits(allowanceAB, decimals)} AP)`);

              // Regla estricta: NO enviar si no alcanza (nunca "todo" ni parciales).
              if (balanceA < requested) {
                const errorMsg = `INSUFFICIENT_BALANCE:have=${balanceA.toString()} need=${requested.toString()}`;
                console.error(`[OUTBOX] ❌ ${offer_id}: ${errorMsg}`);
                markOutbox(offer_id, 'FAILED', errorMsg, () => {});
                return;
              }
              
              // Si no hay suficiente allowance, intentar hacer approve automáticamente
              if (allowanceAB < requested) {
                console.log(`[OUTBOX] Allowance insuficiente. Intentando approve automático...`);
                const approveSuccess = await ensureAllowance(from, requested);
                
                if (!approveSuccess) {
                  const errorMsg = `INSUFFICIENT_ALLOWANCE:have=${allowanceAB.toString()} need=${requested.toString()} (approve automático falló)`;
                  console.error(`[OUTBOX] ❌ ${offer_id}: ${errorMsg}`);
                  markOutbox(offer_id, 'FAILED', errorMsg, () => {});
                  return;
                }
                
                // Verificar allowance nuevamente después del approve
                const newAllowance = await tokenContract.allowance(from, wallet.address);
                console.log(`[OUTBOX] ✅ Approve exitoso. Nuevo allowance: ${newAllowance.toString()} wei`);
                
                if (newAllowance < requested) {
                  const errorMsg = `INSUFFICIENT_ALLOWANCE_AFTER_APPROVE:have=${newAllowance.toString()} need=${requested.toString()}`;
                  console.error(`[OUTBOX] ❌ ${offer_id}: ${errorMsg}`);
                  markOutbox(offer_id, 'FAILED', errorMsg, () => {});
                  return;
                }
              }

              // Log defensivo para auditoría
              console.log(`[OUTBOX] offer_id=${offer_id}`);
              console.log(`  from(A)=${from} balanceA=${balanceA.toString()}`);
              console.log(`  to(B)  =${to}   allowanceAB=${allowanceAB.toString()}`);
              console.log(`  amount_ap_str="${amountStr}"`);
              console.log(`  requestedWei=${requested.toString()}`);
              
              // Validación adicional: verificar que requested no sea igual al balance completo
              if (requested.toString() === balanceA.toString()) {
                console.error(`⚠️  ADVERTENCIA: El monto solicitado (${requested.toString()}) es igual al balance total (${balanceA.toString()})`);
                console.error(`   Esto podría indicar un error. Verificando amount_ap_str: "${amountStr}"`);
              }
              
              // Convertir a formato legible para logs
              const requestedFormatted = ethers.formatUnits(requested, decimals);
              const balanceAFormatted = ethers.formatUnits(balanceA, decimals);
              console.log(`  Monto solicitado: ${requestedFormatted} AP`);
              console.log(`  Balance disponible: ${balanceAFormatted} AP`);

              // Ejecutar exactamente el monto solicitado
              let tx;
              try {
                console.log(`  🚀 Ejecutando transferFrom(${from}, ${to}, ${requested.toString()})`);
                tx = await tokenContract.transferFrom(from, to, requested);
              } catch (e) {
                markOutbox(offer_id, 'FAILED', `TRANSFER_FROM_ERROR:${String(e?.message || e)}`, () => {});
                return;
              }

              // Persistir hash PENDING inmediatamente para evitar duplicados
              const ts0 = Math.floor(Date.now() / 1000);
              db.run(
                `UPDATE vouchers SET tx_hash=?, status=?, onchain_status=?, updated_at=? WHERE offer_id=?`,
                [tx.hash, 'PENDING', 'PENDING', ts0, offer_id],
                () => {}
              );

              // Esperar confirmaciones
              try {
                const receipt = await tx.wait(CONFIRMATIONS);
                const ts1 = Math.floor(Date.now() / 1000);
                
                // Verificar balances después de la transacción
                const [balanceFromAfter, balanceToAfter] = await Promise.all([
                  tokenContract.balanceOf(from),
                  tokenContract.balanceOf(to)
                ]);
                
                console.log(`  ✅ Transacción confirmada: ${tx.hash}`);
                console.log(`  Balance FROM después: ${ethers.formatUnits(balanceFromAfter, decimals)} AP`);
                console.log(`  Balance TO después: ${ethers.formatUnits(balanceToAfter, decimals)} AP`);
                
                if (receipt.status !== 1) {
                  console.error(`  ⚠️  Receipt status: ${receipt.status} (debería ser 1)`);
                }
                
                db.run(
                  `UPDATE vouchers SET status=?, onchain_status=?, updated_at=? WHERE offer_id=?`,
                  ['SUBIDO_OK', 'CONFIRMED', ts1, offer_id],
                  () => {}
                );
                markOutbox(offer_id, 'SENT', null, () => {});
              } catch (waitErr) {
                console.error(`  ❌ Error esperando confirmación: ${waitErr?.message || waitErr}`);
                markOutbox(offer_id, 'FAILED', `CONFIRM_ERROR:${String(waitErr?.message || waitErr)}`, () => {});
              }
            } catch (errTx) {
              markOutbox(offer_id, 'FAILED', String(errTx && errTx.message || errTx), () => {});
            }
          });
        }
      );
    }
  });
}

setInterval(processOutboxOnce, 10000);

// Health check
app.get('/health', (req, res) => {
  res.status(200).json({ status: 'OK', timestamp: Date.now() });
});

const HOST = process.env.HOST || '0.0.0.0'; // Escuchar en todas las interfaces para permitir conexiones desde la red local
app.listen(PORT, HOST, () => {
  console.log(`Servidor escuchando en ${HOST}:${PORT}`);
  console.log(`RPC activo: ${RPC_URL}`);
  console.log(`Contrato: ${CONTRACT_ADDRESS}`);
  processOutboxOnce();
});


// ──────────────────────────── DEBUG ENDPOINTS ────────────────────────────
// SOLO PARA DEBUG — bórralo en prod
app.post('/v1/debug/canonical', (req, res) => {
  try {
    const {
      offer_id, amount_ap, asset, expiry,
      seller_address, buyer_address
    } = req.body || {};
    const base = {
      offer_id: String(offer_id),
      amount_ap: String(amount_ap),
      asset: String(asset),
      expiry: Number(expiry),
      seller_address: String(seller_address).toLowerCase(),
      buyer_address: String(buyer_address).toLowerCase()
    };
    const canonical = canonicalizePaymentBase(base);
    res.status(200).json({ canonical });
  } catch (e) {
    res.status(400).json({ error: String(e.message) });
  }
});

// GET /v1/debug/outbox - Ver estado del outbox
app.get('/v1/debug/outbox', async (req, res) => {
  try {
    db.all(`
      SELECT o.offer_id, o.state, o.last_error, o.created_at, o.updated_at,
             v.buyer_address, v.seller_address, v.amount_ap_str, v.tx_hash, v.status, v.onchain_status
      FROM outbox o
      LEFT JOIN vouchers v ON o.offer_id = v.offer_id
      ORDER BY o.updated_at DESC
      LIMIT 20
    `, [], async (err, rows) => {
      if (err) {
        return res.status(500).json({ error: 'Error consultando outbox', error_code: 'DB_ERROR' });
      }
      
      const results = await Promise.all(rows.map(async (row) => {
        let balance = null;
        let allowance = null;
        
        if (row.buyer_address) {
          try {
            const decimals = await getDecimals();
            balance = await tokenContract.balanceOf(row.buyer_address);
            allowance = await tokenContract.allowance(row.buyer_address, wallet.address);
            return {
              offer_id: row.offer_id,
              state: row.state,
              last_error: row.last_error,
              created_at: new Date(row.created_at * 1000).toISOString(),
              updated_at: new Date(row.updated_at * 1000).toISOString(),
              buyer_address: row.buyer_address,
              seller_address: row.seller_address,
              amount_ap_str: row.amount_ap_str,
              tx_hash: row.tx_hash,
              status: row.status,
              onchain_status: row.onchain_status,
              balance_ap: ethers.formatUnits(balance, decimals),
              allowance_ap: ethers.formatUnits(allowance, decimals),
              balance_wei: balance.toString(),
              allowance_wei: allowance.toString()
            };
          } catch (e) {
            return {
              offer_id: row.offer_id,
              state: row.state,
              last_error: row.last_error || `Error consultando balance/allowance: ${e.message}`,
              created_at: new Date(row.created_at * 1000).toISOString(),
              updated_at: new Date(row.updated_at * 1000).toISOString(),
              buyer_address: row.buyer_address,
              seller_address: row.seller_address,
              amount_ap_str: row.amount_ap_str,
              tx_hash: row.tx_hash,
              status: row.status,
              onchain_status: row.onchain_status
            };
          }
        }
        
        return {
          offer_id: row.offer_id,
          state: row.state,
          last_error: row.last_error,
          created_at: new Date(row.created_at * 1000).toISOString(),
          updated_at: new Date(row.updated_at * 1000).toISOString(),
          buyer_address: row.buyer_address,
          seller_address: row.seller_address,
          amount_ap_str: row.amount_ap_str,
          tx_hash: row.tx_hash,
          status: row.status,
          onchain_status: row.onchain_status
        };
      }));
      
      res.status(200).json({
        contract_address: CONTRACT_ADDRESS,
        mother_address: wallet.address,
        outbox_items: results
      });
    });
  } catch (e) {
    res.status(500).json({ error: String(e.message) });
  }
});

// GET /v1/debug/daily-limit/{buyer_address} - Ver qué está contando para el límite diario
app.get('/v1/debug/daily-limit/:buyer_address', (req, res) => {
  try {
    const buyerAddress = String(req.params.buyer_address).toLowerCase();
    const startOfDay = Math.floor(new Date().setUTCHours(0, 0, 0, 0) / 1000);
    
    db.all(
      `SELECT offer_id, amount_ap_str, status, created_at, tx_hash
       FROM vouchers
       WHERE LOWER(buyer_address) = ?
         AND created_at >= ?
         AND status IN ('SUBIDO_OK','PENDING','RECEIVED')
       ORDER BY created_at DESC`,
      [buyerAddress, startOfDay],
      (err, rows) => {
        if (err) {
          return res.status(500).json({ error: 'Error consultando DB', error_code: 'DB_ERROR' });
        }
        
        const total = rows.reduce((sum, row) => sum + Number(row.amount_ap_str || 0), 0);
        
        res.status(200).json({
          buyer_address: buyerAddress,
          start_of_day: new Date(startOfDay * 1000).toISOString(),
          total_today: total,
          transactions: rows.map(r => ({
            offer_id: r.offer_id,
            amount_ap: r.amount_ap_str,
            status: r.status,
            tx_hash: r.tx_hash,
            created_at: new Date(r.created_at * 1000).toISOString()
          }))
        });
      }
    );
  } catch (error) {
    res.status(500).json({ error: 'Error interno', error_code: 'INTERNAL_ERROR' });
  }
});

// POST /v1/debug/clean-old-vouchers - Limpiar vouchers antiguos o de prueba
app.post('/v1/debug/clean-old-vouchers', (req, res) => {
  try {
    const { days = 1, statuses = ['RECEIVED', 'PENDING'], dryRun = false } = req.body || {};
    const cutoffTime = Math.floor(Date.now() / 1000) - (days * 24 * 60 * 60);
    
    if (dryRun) {
      // Solo contar, no eliminar
      const placeholders = statuses.map(() => '?').join(',');
      db.get(
        `SELECT COUNT(*) as count, COALESCE(SUM(CAST(amount_ap_str AS REAL)), 0) as total
         FROM vouchers
         WHERE created_at < ? AND status IN (${placeholders})`,
        [cutoffTime, ...statuses],
        (err, row) => {
          if (err) {
            return res.status(500).json({ error: 'Error consultando DB', error_code: 'DB_ERROR' });
          }
          res.status(200).json({
            dry_run: true,
            would_delete: row?.count || 0,
            total_ap: row?.total || 0,
            cutoff_date: new Date(cutoffTime * 1000).toISOString(),
            statuses
          });
        }
      );
    } else {
      // Eliminar realmente
      const placeholders = statuses.map(() => '?').join(',');
      db.run(
        `DELETE FROM vouchers
         WHERE created_at < ? AND status IN (${placeholders})`,
        [cutoffTime, ...statuses],
        function(deleteErr) {
          if (deleteErr) {
            return res.status(500).json({ error: 'Error eliminando vouchers', error_code: 'DB_ERROR' });
          }
          
          // También limpiar outbox huérfano
          db.run(
            `DELETE FROM outbox WHERE offer_id NOT IN (SELECT offer_id FROM vouchers)`,
            [],
            () => {}
          );
          
          res.status(200).json({
            deleted: this.changes,
            cutoff_date: new Date(cutoffTime * 1000).toISOString(),
            statuses
          });
        }
      );
    }
  } catch (error) {
    res.status(500).json({ error: 'Error interno', error_code: 'INTERNAL_ERROR' });
  }
});

