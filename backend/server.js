require('dotenv').config();
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

// Crear proveedor con fallback automático
const fallbacks = urls.map((u) => ({
  provider: new ethers.JsonRpcProvider(u, CHAIN_ID),
  weight: 1,
  stallTimeout: 1000, // si el 1° falla o demora >1s, intenta el siguiente
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
  "function balanceOf(address account) external view returns (uint256)",
  "function decimals() external view returns (uint8)"
];

// Instanciar contrato AgroPuntos
const tokenContract = new ethers.Contract(CONTRACT_ADDRESS, ERC20_ABI, wallet);
console.log(`🔗 Contrato conectado: ${CONTRACT_ADDRESS}`);

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
    }
  });
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
          const decimals = await tokenContract.decimals();
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
      const decimals = await tokenContract.decimals();
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

// Health check
app.get('/health', (req, res) => {
  res.status(200).json({ status: 'OK', timestamp: Date.now() });
});

app.listen(PORT, () => {
  console.log(`Servidor escuchando en puerto ${PORT}`);
  console.log(`RPCs activos:`);
  urls.forEach((u, i) => console.log(`  [${i + 1}] ${u}`));
  console.log(`Contrato: ${CONTRACT_ADDRESS}`);
});

