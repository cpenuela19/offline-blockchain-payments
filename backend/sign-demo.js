
const { Wallet } = require("ethers");
const { randomUUID } = require("crypto");

// ⚠️ LLAVES DE PRUEBA — NO PRODUCCIÓN
const buyer  = new Wallet("0xcd718258ce1ab1714d2e055f3561952d0922068d9175056c5698e7656586e056");
const seller = new Wallet("0x56bcf27275be43ed0bc95635a67698040ac5973683f0e04d99e6a5369ffce6c5");

// Canon estricta que verifica tu backend: asset, buyer_address, expiry, offer_id, seller_address, amount_ap
function canonicalizePaymentBase(base) {
  const payload = {
    asset: String(base.asset),
    buyer_address: String(base.buyer_address).toLowerCase(),
    expiry: Number(base.expiry),
    offer_id: String(base.offer_id),
    seller_address: String(base.seller_address).toLowerCase(),
    amount_ap: String(base.amount_ap)
  };
  return JSON.stringify(payload); // sin espacios
}

// Si pasas un UUID por argv, úsalo; si no, genera uno nuevo
const offerId = process.argv[2] || randomUUID();

// Datos del voucher (ajusta amount/asset/expiry si quieres)
const base = {
  offer_id: offerId,
  amount_ap: "100",
  asset: "AP",
  expiry: 2000000000,
  seller_address: seller.address,
  buyer_address: buyer.address
};

(async () => {
  const canonical = canonicalizePaymentBase(base);
  const sellerSig = await seller.signMessage(canonical);
  const buyerSig  = await buyer.signMessage(canonical);

  console.log("\n=== DATOS PARA CURL ===\n");
  console.log("offer_id      :", base.offer_id);
  console.log("seller_address:", base.seller_address);
  console.log("buyer_address :", base.buyer_address);
  console.log("seller_sig    :", sellerSig);
  console.log("buyer_sig     :", buyerSig);

  // (Opcional) también muestro la cadena canónica por si quieres verificar a mano:
  console.log("\ncanonical     :", canonical);
})();
