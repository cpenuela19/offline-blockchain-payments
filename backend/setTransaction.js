// setTransaction.js — Unifica approve y settle en un solo archivo
// Uso:
//   node backend/setTransaction.js approve    # Hace approve una vez
//   node backend/setTransaction.js settle     # Crea y envía voucher (settle)
//   node backend/setTransaction.js            # Por defecto hace settle

require('dotenv').config();
const { randomUUID } = require('crypto');
const fetch = global.fetch || require('node-fetch');
const { ethers, Wallet } = require('ethers');

const ACTION = process.argv[2] || 'settle'; // 'approve' o 'settle'

// ──────────────────────────── APPROVE ────────────────────────────
async function doApprove() {
  const RPC = process.env.RPC_URL_PRIMARY || process.env.RPC_URL_SECONDARY || process.env.RPC_URL;
  const CHAIN_ID = parseInt(process.env.CHAIN_ID || '11155111', 10);

  const A_PRIV = process.env.PRIV_KEY_A;
  const TOKEN = process.env.CONTRACT_ADDRESS_AP;
  const MOTHER = process.env.MOTHER_ADDRESS;

  if (!A_PRIV || !TOKEN || !MOTHER) {
    console.error('❌ Faltan PRIV_KEY_A, CONTRACT_ADDRESS_AP o MOTHER_ADDRESS en .env');
    process.exit(1);
  }

  const ABI = [
    "function approve(address spender, uint256 value) external returns (bool)",
    "function allowance(address owner, address spender) external view returns (uint256)"
  ];

  const provider = new ethers.JsonRpcProvider(RPC, CHAIN_ID);
  const A = new ethers.Wallet(A_PRIV, provider);
  const ap = new ethers.Contract(TOKEN, ABI, A);

  console.log('🔐 Aprobando transferencia...');
  console.log('Cuenta A (owner):', A.address);
  console.log('Autorizando a la madre (spender):', MOTHER);

  const antes = await ap.allowance(A.address, MOTHER);
  console.log('Allowance antes:', antes.toString());

  const tx = await ap.approve(MOTHER, ethers.MaxUint256);
  console.log('✅ Transacción approve:', tx.hash);
  await tx.wait(1);

  const despues = await ap.allowance(A.address, MOTHER);
  console.log('Allowance después:', despues.toString());
  console.log('✅ Listo: la madre podrá usar transferFrom(A → B) pagando el gas.');
}

// ──────────────────────────── SETTLE ────────────────────────────
function canonicalizePaymentBase({ offer_id, amount_ap, asset, expiry, seller_address, buyer_address }) {
  const payload = {
    asset: String(asset),
    buyer_address: String(buyer_address).toLowerCase(),
    expiry: Number(expiry),
    offer_id: String(offer_id),
    seller_address: String(seller_address).toLowerCase(),
    amount_ap: String(amount_ap),
  };
  return JSON.stringify(payload);
}

async function doSettle() {
  const OFFER_ID = process.env.OFFER_ID || randomUUID();
  const AMOUNT_AP = process.env.AMOUNT_AP || '5';
  const ASSET = 'AP';
  const EXPIRY = Number(process.env.EXPIRY || 2000000000);

  // --- keys / addresses from .env
  const BUYER_PRIV = process.env.PRIV_KEY_A;
  const SELLER_PRIV = process.env.PRIV_KEY_B || null;
  const SELLER_ADDR = (process.env.B_ADDRESS || process.env.SELLER_ADDRESS || '').trim();

  // Validate buyer private key presence
  if (!BUYER_PRIV) {
    console.error('❌ ERROR: PRIV_KEY_A (buyer private key) is required in .env');
    console.error('Set PRIV_KEY_A=0x...');
    process.exit(1);
  }

  let buyer, seller;
  try {
    buyer = new Wallet(BUYER_PRIV);
  } catch (e) {
    console.error('❌ ERROR: invalid PRIV_KEY_A:', e.message || e);
    process.exit(1);
  }

  if (SELLER_PRIV) {
    try {
      seller = new Wallet(SELLER_PRIV);
    } catch (e) {
      console.error('❌ ERROR: invalid PRIV_KEY_B:', e.message || e);
      process.exit(1);
    }
  }

  // Resolve seller address (either from seller wallet or B_ADDRESS)
  const sellerAddressFinal = seller ? seller.address : (SELLER_ADDR || null);

  if (!sellerAddressFinal) {
    console.error('❌ ERROR: seller address unknown. Provide PRIV_KEY_B or B_ADDRESS in .env');
    process.exit(1);
  }

  console.log('📝 Creando voucher...');
  console.log('OFFER_ID:', OFFER_ID);
  console.log('Buyer (A) address used:', buyer.address);
  console.log('Seller (B) address used:', sellerAddressFinal);
  console.log('Amount AP:', AMOUNT_AP);
  console.log('Will sign with: BUYER_PRIV -> yes, SELLER_PRIV ->', !!seller);

  const canonical = canonicalizePaymentBase({
    offer_id: OFFER_ID,
    amount_ap: AMOUNT_AP,
    asset: ASSET,
    expiry: EXPIRY,
    seller_address: sellerAddressFinal,
    buyer_address: buyer.address,
  });

  // Buyer signature (always required)
  const buyer_sig = await buyer.signMessage(canonical);

  // Seller signature optional (only if PRIV_KEY_B provided)
  let seller_sig = null;
  if (seller) {
    seller_sig = await seller.signMessage(canonical);
  }

  const body = {
    offer_id: OFFER_ID,
    amount_ap: AMOUNT_AP,
    asset: ASSET,
    expiry: EXPIRY,
    seller_address: sellerAddressFinal,
    buyer_address: buyer.address,
    buyer_sig,
  };
  if (seller_sig) body.seller_sig = seller_sig;

  console.log('📤 POSTing /v1/vouchers/settle -> backend');
  const res = await fetch('http://localhost:3000/v1/vouchers/settle', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  const txt = await res.text();
  console.log('/settle ->', txt);

  let settleResult;
  try {
    settleResult = JSON.parse(txt);
  } catch (e) {
    console.error('❌ Error parseando respuesta del servidor:', txt);
    process.exit(1);
  }
  
  if (settleResult.error_code) {
    console.error('❌ Error al crear voucher:', settleResult.message || settleResult.error_code);
    process.exit(1);
  }

  // Esperar hasta que la transacción esté confirmada
  console.log('\n⏳ Esperando confirmación de transacción...');
  let attempts = 0;
  const maxAttempts = 120; // 10 minutos máximo (5 segundos * 120)
  
  while (attempts < maxAttempts) {
    await new Promise(resolve => setTimeout(resolve, 5000)); // Esperar 5 segundos
    
    const st = await fetch(`http://localhost:3000/v1/tx/${encodeURIComponent(OFFER_ID)}`);
    const statusText = await st.text();
    let status;
    try {
      status = JSON.parse(statusText);
    } catch (e) {
      console.error('❌ Error parseando estado:', statusText);
      attempts++;
      continue;
    }
    
    if (status.error_code === 'NOT_FOUND') {
      console.log(`[${attempts + 1}/${maxAttempts}] Transacción aún no encontrada, esperando...`);
      attempts++;
      continue;
    }
    
    if (status.tx_hash) {
      console.log(`✅ TX Hash: ${status.tx_hash}`);
      console.log(`📊 Estado: ${status.onchain_status}`);
      
      if (status.onchain_status === 'CONFIRMED') {
        console.log('🎉 ¡Transacción confirmada exitosamente!');
        process.exit(0);
      } else if (status.onchain_status === 'FAILED') {
        console.error('❌ Transacción falló en blockchain');
        process.exit(1);
      } else {
        console.log(`[${attempts + 1}/${maxAttempts}] Estado: ${status.onchain_status}, esperando confirmación...`);
      }
    } else {
      console.log(`[${attempts + 1}/${maxAttempts}] Transacción pendiente, esperando...`);
    }
    
    attempts++;
  }
  
  console.error('⏰ Tiempo de espera agotado. La transacción puede estar aún procesándose.');
  console.log(`💡 Verifica manualmente: http://localhost:3000/v1/tx/${encodeURIComponent(OFFER_ID)}`);
  process.exit(1);
}

// ──────────────────────────── MAIN ────────────────────────────
(async () => {
  try {
    if (ACTION === 'approve') {
      await doApprove();
    } else if (ACTION === 'settle') {
      await doSettle();
    } else {
      console.error(`❌ Acción desconocida: ${ACTION}`);
      console.error('Uso: node backend/setTransaction.js [approve|settle]');
      process.exit(1);
    }
  } catch (error) {
    console.error('❌ Error:', error.message || error);
    process.exit(1);
  }
})();

