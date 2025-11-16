// Cargar .env desde la raíz del proyecto o desde backend/
const path = require('path');
require('dotenv').config({ path: path.resolve(__dirname, '../.env') });
require('dotenv').config({ path: path.resolve(__dirname, '.env') }); // Fallback a backend/.env
const express = require('express');
const cors = require('cors');
const { ethers } = require('ethers');
const sqlite3 = require('sqlite3').verbose();
const rateLimit = require('express-rate-limit');

const app = express();
const PORT = process.env.PORT || 3000;

// ───────────────────────────── Middleware ─────────────────────────────
app.use(cors());
app.use(express.json());

// Rate limiting
const limiter = rateLimit({
  windowMs: parseInt(process.env.RATE_LIMIT_WINDOW_MS) || 60000,
  max: parseInt(process.env.RATE_LIMIT_MAX_REQUESTS) || 30,
  message: 'Demasiadas solicitudes, intenta más tarde'
});
app.use('/v1/', limiter);

// ─────────────────────── Configuración blockchain ─────────────────────
const CHAIN_ID = parseInt(process.env.CHAIN_ID || '11155111');
const PRIVATE_KEY = process.env.PRIVATE_KEY_CUENTA_MADRE;
const CONTRACT_ADDRESS = process.env.CONTRACT_ADDRESS_AP;
const CONFIRMATIONS = parseInt(process.env.CONFIRMATIONS || '1');

// Soporte para múltiples RPCs (Infura/Alchemy + fallback público)
const urls = [
  process.env.RPC_URL_PRIMARY || process.env.RPC_URL, // principal (Infura/Alchemy)
  process.env.RPC_URL_SECONDARY                      // secundario (fallback)
].filter(Boolean);

if (!PRIVATE_KEY || !CONTRACT_ADDRESS || urls.length === 0) {
  console.error('❌ ERROR: Faltan variables de entorno necesarias');
  console.error('Verifica PRIVATE_KEY_CUENTA_MADRE, CONTRACT_ADDRESS_AP y RPC_URL_PRIMARY o RPC_URL');
  process.exit(1);
}

// Crear proveedor con fallback automático (red fijada para evitar "failed to detect network")
const sepoliaNet = ethers.Network.from(11155111); // o usa CHAIN_ID si quieres: ethers.Network.from(CHAIN_ID)

const fallbacks = urls.map((u) => ({
  provider: new ethers.JsonRpcProvider(
    u,
    sepoliaNet,                 // fija la red (evita auto-detección)
    { staticNetwork: sepoliaNet } // desactiva el sondeo de red
  ),
  weight: 1,
  stallTimeout: 1500, // pequeño margen más alto
}));

const provider = new ethers.FallbackProvider(fallbacks);


// Conectar la wallet (cuenta madre)
const wallet = new ethers.Wallet(PRIVATE_KEY, provider);
console.log(`✅ Cuenta madre conectada: ${wallet.address}`);
console.log(`🌐 RPCs configurados:`);
urls.forEach((u, i) => console.log(`   [${i + 1}] ${u}`));

// ABI mínimo del ERC-20
const ERC20_ABI = [
  "function transfer(address to, uint256 amount) external returns (bool)",
  "function transferFrom(address from, address to, uint256 amount) external returns (bool)",
  "function approve(address spender, uint256 amount) external returns (bool)",
  "function allowance(address owner, address spender) external view returns (uint256)",
  "function balanceOf(address account) external view returns (uint256)",
  "function decimals() external view returns (uint8)"
];

// Instanciar contrato AgroPuntos
const tokenContract = new ethers.Contract(CONTRACT_ADDRESS, ERC20_ABI, wallet);
console.log(`🔗 Contrato conectado: ${CONTRACT_ADDRESS}`);

// Límites offline (pueden ajustarse por env)
const OFFLINE_VOUCHER_MAX_AP = Number(process.env.OFFLINE_VOUCHER_MAX_AP || 200);
const OFFLINE_BUYER_DAILY_MAX_AP = Number(process.env.OFFLINE_BUYER_DAILY_MAX_AP || 1000);

let DECIMALS_CACHE = null;
async function getDecimals() {
  if (DECIMALS_CACHE === null) {
    DECIMALS_CACHE = await tokenContract.decimals();
  }
  return DECIMALS_CACHE;
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

// POST /v1/vouchers/settle
app.post('/v1/vouchers/settle', async (req, res) => {
  try {
    const {
      offer_id, amount_ap, asset, expiry,
      seller_address, seller_sig,
      buyer_address, buyer_sig
    } = req.body || {};

    if (!offer_id || !amount_ap || !asset || !expiry || !seller_address || !seller_sig || !buyer_address || !buyer_sig) {
      return res.status(400).json({ error_code: 'BAD_REQUEST', message: 'Missing fields' });
    }
    if (!/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(String(offer_id))) {
      return res.status(400).json({ error_code: 'BAD_OFFER_ID', message: 'offer_id must be UUID v4' });
    }
    if (String(asset) !== 'AP') {
      return res.status(400).json({ error_code: 'BAD_ASSET', message: 'asset must be "AP"' });
    }
    const now = Math.floor(Date.now() / 1000);
    if (Number(expiry) <= now) {
      return res.status(409).json({ error_code: 'EXPIRED', message: 'Voucher expired' });
    }

    if (!isHexAddress(seller_address) || !isHexAddress(buyer_address)) {
      return res.status(400).json({ error_code: 'BAD_ADDRESS', message: 'seller_address y buyer_address deben ser 0x...' });
    }

    const amountNumeric = Number(amount_ap);
    if (!Number.isFinite(amountNumeric) || amountNumeric <= 0) {
      return res.status(400).json({ error_code: 'BAD_AMOUNT', message: 'amount_ap inválido' });
    }
    if (amountNumeric > OFFLINE_VOUCHER_MAX_AP) {
      return res.status(429).json({ error_code: 'RISK_LIMITS', message: `amount_ap excede el máximo por voucher (${OFFLINE_VOUCHER_MAX_AP} AP)` });
    }

    const sellerLower = seller_address.toLowerCase();
    const buyerLower = buyer_address.toLowerCase();

    const base = {
      offer_id,
      amount_ap: String(amount_ap),
      asset,
      expiry: Number(expiry),
      seller_address: sellerLower,
      buyer_address: buyerLower
    };
    let canonical;
    try {
      canonical = canonicalizePaymentBase(base);
    } catch (e) {
      return res.status(400).json({ error_code: 'BAD_CANONICAL', message: String(e.message) });
    }

    const okSeller = verifySignature(canonical, seller_sig, sellerLower);
    const okBuyer = verifySignature(canonical, buyer_sig, buyerLower);
    if (!okSeller || !okBuyer) {
      return res.status(422).json({ error_code: 'INVALID_SIGNATURE', message: 'seller_sig or buyer_sig invalid' });
    }

    const startOfDay = Math.floor(new Date().setUTCHours(0, 0, 0, 0) / 1000);

    db.get(
      `SELECT COALESCE(SUM(CAST(amount_ap_str AS REAL)), 0) AS total
       FROM vouchers
       WHERE LOWER(buyer_address) = ?
         AND created_at >= ?
         AND status IN ('SUBIDO_OK','PENDING','RECEIVED')
         AND offer_id <> ?`,
      [buyerLower, startOfDay, offer_id],
      (sumErr, aggregate) => {
        if (sumErr) {
          console.error('Error evaluando límites de riesgo:', sumErr);
          return res.status(500).json({ error_code: 'DB_ERROR', message: 'Risk limits check failed' });
        }
        const totalToday = Number(aggregate?.total || 0);
        if (totalToday + amountNumeric > OFFLINE_BUYER_DAILY_MAX_AP) {
          return res.status(429).json({
            error_code: 'RISK_LIMITS',
            message: `buyer_address excede el límite diario (${OFFLINE_BUYER_DAILY_MAX_AP} AP)`
          });
        }

        db.get('SELECT offer_id, tx_hash, status FROM vouchers WHERE offer_id=?', [offer_id], (err, row) => {
          if (err) return res.status(500).json({ error_code: 'DB_ERROR', message: 'DB read error' });

          if (row && row.tx_hash && row.status === 'SUBIDO_OK') {
            return res.status(200).json({ status: 'already_settled', tx_hash: row.tx_hash });
          }

          const ts = Math.floor(Date.now() / 1000);
          const upsert = () => {
            db.run(
              `INSERT INTO vouchers (
                 offer_id, amount_ap, buyer_alias, seller_alias, tx_hash, status, onchain_status, created_at, updated_at,
                 payload_canonical, seller_address, buyer_address, seller_sig, buyer_sig, expiry, asset, amount_ap_str
               ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
               ON CONFLICT(offer_id) DO UPDATE SET
                 payload_canonical=excluded.payload_canonical,
                 seller_address=excluded.seller_address,
                 buyer_address=excluded.buyer_address,
                 seller_sig=excluded.seller_sig,
                 buyer_sig=excluded.buyer_sig,
                 expiry=excluded.expiry,
                 asset=excluded.asset,
                 amount_ap_str=excluded.amount_ap_str,
                 updated_at=excluded.updated_at`,
              [
                offer_id,
                Math.floor(amountNumeric), '', '', null,
                'RECEIVED', 'PENDING',
                ts, ts,
                canonical, sellerLower, buyerLower, seller_sig, buyer_sig,
                Number(expiry), 'AP', String(amount_ap)
              ],
              (insErr) => {
                if (insErr) return res.status(500).json({ error_code: 'DB_ERROR', message: 'DB insert/update error' });
                enqueueOutbox(offer_id, (qErr) => {
                  if (qErr) return res.status(500).json({ error_code: 'OUTBOX_ERROR', message: 'enqueue failed' });
                  return res.status(200).json({ status: 'queued' });
                });
              }
            );
          };

          if (!row) return upsert();
          upsert();
        });
      }
    );
  } catch (e) {
    console.error('Error in /v1/vouchers/settle:', e);
    return res.status(500).json({ error_code: 'INTERNAL_ERROR', message: 'Unexpected server error' });
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
              const decimals = await getDecimals();

              const amountStr = v.amount_ap_str;               // viene de /settle
              if (!amountStr) {
                markOutbox(offer_id, 'FAILED', 'MISSING_amount_ap_str', () => {});
                return;
              }
              const requested = ethers.parseUnits(String(amountStr), decimals);

              const from = v.buyer_address;                    // A
              const to   = v.seller_address;                   // B

              if (!/^0x[a-fA-F0-9]{40}$/.test(from)) {
                markOutbox(offer_id, 'FAILED', 'BAD_BUYER_ADDRESS', () => {});
                return;
              }
              if (!/^0x[a-fA-F0-9]{40}$/.test(to)) {
                markOutbox(offer_id, 'FAILED', 'BAD_SELLER_ADDRESS', () => {});
                return;
              }

              // Chequear balance y allowance de A
              const [balanceA, allowanceAB] = await Promise.all([
                tokenContract.balanceOf(from),
                tokenContract.allowance(from, wallet.address)   // madre como spender
              ]);

              // Regla estricta: NO enviar si no alcanza (nunca "todo" ni parciales).
              if (balanceA < requested) {
                markOutbox(offer_id, 'FAILED', `INSUFFICIENT_BALANCE:have=${balanceA.toString()} need=${requested.toString()}`, () => {});
                return;
              }
              if (allowanceAB < requested) {
                markOutbox(offer_id, 'FAILED', `INSUFFICIENT_ALLOWANCE:have=${allowanceAB.toString()} need=${requested.toString()}`, () => {});
                return;
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
  console.log(`RPCs activos:`);
  urls.forEach((u, i) => console.log(`  [${i + 1}] ${u}`));
  console.log(`Contrato: ${CONTRACT_ADDRESS}`);
  console.log(`📊 Límites configurados:`);
  console.log(`   - Máximo por voucher: ${OFFLINE_VOUCHER_MAX_AP} AP`);
  console.log(`   - Máximo diario por buyer: ${OFFLINE_BUYER_DAILY_MAX_AP} AP`);
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
          limit: OFFLINE_BUYER_DAILY_MAX_AP,
          remaining: Math.max(0, OFFLINE_BUYER_DAILY_MAX_AP - total),
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

