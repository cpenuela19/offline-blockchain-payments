// backend/settle-now.cjs
// CommonJS. Reads keys from .env. Signs voucher with A (buyer).
// If PRIV_KEY_B present it will sign seller too; otherwise it will send seller as address only.
// Use: node backend/settle-now.cjs

require('dotenv').config();
const { randomUUID } = require('crypto');
const fetch = global.fetch || require('node-fetch');
const { Wallet } = require('ethers');

const OFFER_ID = process.env.OFFER_ID || randomUUID();
const AMOUNT_AP = process.env.AMOUNT_AP || '100';
const ASSET = 'AP';
const EXPIRY = Number(process.env.EXPIRY || 2000000000);

// --- keys / addresses from .env
const BUYER_PRIV = process.env.PRIV_KEY_A;      // REQUIRED
const SELLER_PRIV = process.env.PRIV_KEY_B || null; // optional
const SELLER_ADDR  = (process.env.B_ADDRESS || process.env.SELLER_ADDRESS || '').trim();

// Validate buyer private key presence
if (!BUYER_PRIV) {
  console.error('ERROR: PRIV_KEY_A (buyer private key) is required in .env');
  console.error('Set PRIV_KEY_A=0x...');
  process.exit(1);
}

let buyer, seller;
try {
  buyer = new Wallet(BUYER_PRIV);
} catch (e) {
  console.error('ERROR: invalid PRIV_KEY_A:', e.message || e);
  process.exit(1);
}

if (SELLER_PRIV) {
  try {
    seller = new Wallet(SELLER_PRIV);
  } catch (e) {
    console.error('ERROR: invalid PRIV_KEY_B:', e.message || e);
    process.exit(1);
  }
}

// Resolve seller address (either from seller wallet or B_ADDRESS)
const sellerAddressFinal = seller ? seller.address : (SELLER_ADDR || null);

if (!sellerAddressFinal) {
  console.error('ERROR: seller address unknown. Provide PRIV_KEY_B or B_ADDRESS in .env');
  process.exit(1);
}

console.log('--- settle-now starting ---');
console.log('OFFER_ID:', OFFER_ID);
console.log('Buyer (A) address used:', buyer.address);
console.log('Seller (B) address used:', sellerAddressFinal);
console.log('Will sign with: BUYER_PRIV -> yes, SELLER_PRIV ->', !!seller);

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

(async () => {
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

  console.log('POSTing /v1/vouchers/settle -> backend');
  const res = await fetch('http://localhost:3000/v1/vouchers/settle', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  const txt = await res.text();
  console.log('/settle ->', txt);

  // Immediately query status
  const st = await fetch(`http://localhost:3000/v1/tx/${encodeURIComponent(OFFER_ID)}`);
  console.log('/tx    ->', await st.text());
})();
