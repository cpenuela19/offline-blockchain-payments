import { randomUUID } from "crypto";

// settle-now.js — firma y envía /v1/vouchers/settle en un paso
const { Wallet } = require("ethers");

// Datos de prueba 
const OFFER_ID = randomUUID();
const AMOUNT_AP = "100";
const ASSET = "AP";
const EXPIRY = 2000000000;

// TEST KEYS — NO PRODUCCIÓN
const BUYER_PRIV  = "0xcd718258ce1ab1714d2e055f3561952d0922068d9175056c5698e7656586e056";
const SELLER_PRIV = "0x56bcf27275be43ed0bc95635a67698040ac5973683f0e04d99e6a5369ffce6c5";

const buyer  = new Wallet(BUYER_PRIV);
const seller = new Wallet(SELLER_PRIV);

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
    seller_address: seller.address,
    buyer_address: buyer.address,
  });

  const seller_sig = await seller.signMessage(canonical);
  const buyer_sig  = await buyer.signMessage(canonical);

  const body = {
    offer_id: OFFER_ID,
    amount_ap: AMOUNT_AP,
    asset: ASSET,
    expiry: EXPIRY,
    seller_address: seller.address,
    seller_sig,
    buyer_address: buyer.address,
    buyer_sig,
  };

  const res = await fetch("http://localhost:3000/v1/vouchers/settle", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  const txt = await res.text();
  console.log("/settle →", txt);

  const st = await fetch(`http://localhost:3000/v1/tx/${encodeURIComponent(OFFER_ID)}`);
  console.log("/tx    →", await st.text());
})().catch(e => console.error("ERROR:", e?.message || e));

