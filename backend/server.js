require('dotenv').config();
const express = require('express');
const cors = require('cors');
const { ethers } = require('ethers');
const sqlite3 = require('sqlite3').verbose();
const rateLimit = require('express-rate-limit');

const app = express();
const PORT = process.env.PORT || 3000;

// Middleware
app.use(cors());
app.use(express.json());

// Rate limiting
const limiter = rateLimit({
  windowMs: parseInt(process.env.RATE_LIMIT_WINDOW_MS) || 60000,
  max: parseInt(process.env.RATE_LIMIT_MAX_REQUESTS) || 30,
  message: 'Demasiadas solicitudes, intenta más tarde'
});
app.use('/v1/', limiter);

// Configuración de blockchain
const RPC_URL = process.env.RPC_URL;
const CHAIN_ID = parseInt(process.env.CHAIN_ID || '11155111');
const PRIVATE_KEY = process.env.PRIVATE_KEY_CUENTA_MADRE;
const CONTRACT_ADDRESS = process.env.CONTRACT_ADDRESS_AP;
const CONFIRMATIONS = parseInt(process.env.CONFIRMATIONS || '1');

if (!PRIVATE_KEY || !CONTRACT_ADDRESS || !RPC_URL) {
  console.error('ERROR: Faltan variables de entorno necesarias');
  process.exit(1);
}

// Conectar a la red
const provider = new ethers.JsonRpcProvider(RPC_URL);
const wallet = new ethers.Wallet(PRIVATE_KEY, provider);
console.log(`Cuenta madre conectada: ${wallet.address}`);

// ABI mínimo del ERC-20 (transfer)
const ERC20_ABI = [
  "function transfer(address to, uint256 amount) external returns (bool)",
  "function balanceOf(address account) external view returns (uint256)",
  "function decimals() external view returns (uint8)"
];

const tokenContract = new ethers.Contract(CONTRACT_ADDRESS, ERC20_ABI, wallet);

// Base de datos SQLite
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

// Helper: Mapeo alias -> address (simplificado, en producción usar DB)
const aliasToAddress = {
  'Juan': '0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb', // Dummy - cambiar por dirección real
  'Marta': '0x8ba1f109551bD432803012645Hac136c22C1779', // Dummy - cambiar por dirección real
};

function getAddressFromAlias(alias) {
  return aliasToAddress[alias] || null;
}

// Helper: Validar request
function validateVoucherRequest(req) {
  const { offer_id, amount_ap, buyer_alias, seller_alias, created_at } = req.body;
  
  if (!offer_id || typeof offer_id !== 'string') {
    return { valid: false, error: 'offer_id es obligatorio y debe ser string' };
  }
  
  if (!amount_ap || typeof amount_ap !== 'number' || amount_ap <= 0) {
    return { valid: false, error: 'amount_ap debe ser un número mayor a 0' };
  }
  
  if (amount_ap > 100000) {
    return { valid: false, error: 'amount_ap excede el límite máximo (100,000 AP)' };
  }
  
  if (!buyer_alias || !seller_alias) {
    return { valid: false, error: 'buyer_alias y seller_alias son obligatorios' };
  }
  
  const now = Math.floor(Date.now() / 1000);
  if (!created_at || created_at < now - 3600 || created_at > now + 300) {
    return { valid: false, error: 'created_at debe ser razonable (última hora)' };
  }
  
  return { valid: true };
}

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
    
    // Verificar idempotencia
    db.get('SELECT offer_id FROM vouchers WHERE offer_id = ?', [offer_id], async (err, row) => {
      if (err) {
        console.error('Error consultando DB:', err);
        return res.status(500).json({ 
          error: 'Error interno',
          error_code: 'DB_ERROR'
        });
      }
      
      if (row) {
        // Ya existe, devolver estado actual
        db.get('SELECT tx_hash, status FROM vouchers WHERE offer_id = ?', [offer_id], (err, existing) => {
          if (existing && existing.tx_hash) {
            return res.status(200).json({
              offer_id,
              tx_hash: existing.tx_hash,
              status: 'SUBIDO_OK'
            });
          }
          return res.status(409).json({
            error: 'offer_id duplicado',
            error_code: 'DUPLICATE_OFFER_ID'
          });
        });
        return;
      }
      
      // Obtener dirección del vendedor
      const sellerAddress = getAddressFromAlias(seller_alias);
      if (!sellerAddress) {
        return res.status(400).json({
          error: `No se encontró dirección para alias: ${seller_alias}`,
          error_code: 'ALIAS_NOT_FOUND'
        });
      }
      
      // Convertir amount_ap a wei (asumiendo 18 decimals)
      const decimals = await tokenContract.decimals();
      const amountWei = ethers.parseUnits(amount_ap.toString(), decimals);
      
      // Insertar en DB primero (status PENDING)
      const now = Math.floor(Date.now() / 1000);
      db.run(
        'INSERT INTO vouchers (offer_id, amount_ap, buyer_alias, seller_alias, tx_hash, status, onchain_status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)',
        [offer_id, amount_ap, buyer_alias, seller_alias, null, 'PENDING', 'PENDING', created_at, now],
        async (err) => {
          if (err) {
            console.error('Error insertando voucher:', err);
            return res.status(500).json({
              error: 'Error guardando voucher',
              error_code: 'DB_ERROR'
            });
          }
          
          try {
            // Ejecutar transferencia
            const tx = await tokenContract.transfer(sellerAddress, amountWei);
            console.log(`Transacción enviada: ${tx.hash}`);
            
            // Actualizar con tx_hash
            db.run(
              'UPDATE vouchers SET tx_hash = ?, status = ?, onchain_status = ?, updated_at = ? WHERE offer_id = ?',
              [tx.hash, 'PENDING', 'PENDING', now, offer_id],
              (err) => {
                if (err) {
                  console.error('Error actualizando tx_hash:', err);
                }
              }
            );
            
            // Esperar confirmaciones
            const receipt = await tx.wait(CONFIRMATIONS);
            
            // Actualizar estado final
            const finalStatus = receipt.status === 1 ? 'SUBIDO_OK' : 'FAILED';
            const finalOnchainStatus = receipt.status === 1 ? 'CONFIRMED' : 'FAILED';
            
            db.run(
              'UPDATE vouchers SET status = ?, onchain_status = ?, updated_at = ? WHERE offer_id = ?',
              [finalStatus, finalOnchainStatus, Math.floor(Date.now() / 1000), offer_id],
              (err) => {
                if (err) {
                  console.error('Error actualizando estado final:', err);
                }
              }
            );
            
            // Responder inmediatamente con tx_hash
            res.status(200).json({
              offer_id,
              tx_hash: tx.hash,
              status: 'SUBIDO_OK'
            });
          } catch (txError) {
            console.error('Error en transacción:', txError);
            
            // Actualizar estado a ERROR
            db.run(
              'UPDATE vouchers SET status = ?, onchain_status = ?, updated_at = ? WHERE offer_id = ?',
              ['ERROR', 'FAILED', Math.floor(Date.now() / 1000), offer_id],
              () => {}
            );
            
            res.status(500).json({
              error: `Error ejecutando transacción: ${txError.message}`,
              error_code: 'TX_ERROR'
            });
          }
        }
      );
    });
  } catch (error) {
    console.error('Error en POST /v1/vouchers:', error);
    res.status(500).json({
      error: 'Error interno del servidor',
      error_code: 'INTERNAL_ERROR'
    });
  }
});

// GET /v1/tx/{offer_id}
app.get('/v1/tx/:offer_id', async (req, res) => {
  try {
    const { offer_id } = req.params;
    
    db.get('SELECT tx_hash, onchain_status FROM vouchers WHERE offer_id = ?', [offer_id], async (err, row) => {
      if (err) {
        return res.status(500).json({
          error: 'Error consultando DB',
          error_code: 'DB_ERROR'
        });
      }
      
      if (!row) {
        return res.status(404).json({
          error: 'Transacción no encontrada',
          error_code: 'NOT_FOUND'
        });
      }
      
      // Si hay tx_hash, verificar estado en blockchain
      if (row.tx_hash) {
        try {
          const receipt = await provider.getTransactionReceipt(row.tx_hash);
          const onchainStatus = receipt ? (receipt.status === 1 ? 'CONFIRMED' : 'FAILED') : 'PENDING';
          
          // Actualizar en DB
          db.run(
            'UPDATE vouchers SET onchain_status = ?, updated_at = ? WHERE offer_id = ?',
            [onchainStatus, Math.floor(Date.now() / 1000), offer_id],
            () => {}
          );
          
          return res.status(200).json({
            offer_id,
            tx_hash: row.tx_hash,
            onchain_status: onchainStatus
          });
        } catch (blockchainError) {
          // Si no hay receipt aún, puede estar pendiente
          return res.status(200).json({
            offer_id,
            tx_hash: row.tx_hash,
            onchain_status: row.onchain_status || 'PENDING'
          });
        }
      }
      
      res.status(200).json({
        offer_id,
        tx_hash: null,
        onchain_status: 'PENDING'
      });
    });
  } catch (error) {
    console.error('Error en GET /v1/tx:', error);
    res.status(500).json({
      error: 'Error interno del servidor',
      error_code: 'INTERNAL_ERROR'
    });
  }
});

// GET /v1/balance/{alias}
app.get('/v1/balance/:alias', async (req, res) => {
  try {
    const { alias } = req.params;
    const address = getAddressFromAlias(alias);
    
    if (!address) {
      return res.status(404).json({
        error: `Alias no encontrado: ${alias}`,
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
    res.status(500).json({
      error: 'Error interno del servidor',
      error_code: 'INTERNAL_ERROR'
    });
  }
});

// Health check
app.get('/health', (req, res) => {
  res.status(200).json({ status: 'OK', timestamp: Date.now() });
});

app.listen(PORT, () => {
  console.log(`Servidor escuchando en puerto ${PORT}`);
  console.log(`RPC: ${RPC_URL}`);
  console.log(`Contrato: ${CONTRACT_ADDRESS}`);
});

