// settle_test_vector.js
// Genera un vector de prueba (canonical + firma) para validar que Android y backend
// hablan el mismo idioma criptográfico.

require('dotenv').config();
const { ethers } = require('ethers');

// Función de canonicalización (misma que server.js)
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

(async () => {
  const A_PRIV = process.env.PRIV_KEY_A;
  const B_ADDRESS = process.env.B_ADDRESS || '0x8846f77a51371269a9e84310cc978154adbf7cf8';

  if (!A_PRIV) {
    console.error('❌ ERROR: PRIV_KEY_A no encontrado en .env');
    process.exit(1);
  }

  const wallet = new ethers.Wallet(A_PRIV);

  // Vector de prueba fijo
  const base = {
    asset: 'AP',
    buyer_address: wallet.address,
    expiry: 1893456000, // Timestamp fijo para reproducibilidad
    offer_id: '550e8400-e29b-41d4-a716-446655440000', // UUID fijo
    seller_address: B_ADDRESS.toLowerCase(),
    amount_ap: '50'
  };

  const canonical = canonicalizePaymentBase(base);
  const messageHash = ethers.hashMessage(canonical);
  const sig = await wallet.signMessage(canonical);

  const output = {
    canonical,
    messageHash,
    address: wallet.address,
    signature: sig,
    base: {
      ...base,
      buyer_address: wallet.address // Incluir para referencia
    }
  };

  console.log('\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
  console.log('📋 VECTOR DE PRUEBA CRIPTOGRÁFICO');
  console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n');
  console.log(JSON.stringify(output, null, 2));
  console.log('\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
  console.log('✅ Guarda este JSON como "golden vector" para probar Android');
  console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n');
})();

