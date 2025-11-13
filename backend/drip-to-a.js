// backend/drip-to-a.js
// Goteo de ETH Sepolia desde la madre → A para que A pueda firmar approve().
// Evita FallbackProvider (que rompe si un RPC reporta chainId distinto).
// Selecciona un RPC que confirme CHAIN_ID con sonda + reintentos.
// .env necesarios:
//   RPC_URL_PRIMARY, RPC_URL_SECONDARY  (opcionales: RPC_URL_TERTIARY, RPC_URL_QUATERNARY)
//   CHAIN_ID=11155111
//   PRIVATE_KEY_CUENTA_MADRE=0x...
//   PRIV_KEY_A=0x...  (o A_ADDRESS=0x...)
//   (opcionales) DRIP_A_AMOUNT_ETH / DRIP_AMOUNT_ETH, MIN_A_TARGET_ETH / MIN_TARGET_ETH
//
// Uso:
//   node backend/drip-to-a.js

require('dotenv').config();
const { ethers } = require('ethers');

const CHAIN_ID   = parseInt(process.env.CHAIN_ID || '11155111', 10);
const MOTHER_PRIV = process.env.PRIVATE_KEY_CUENTA_MADRE;

// URLs en orden de prioridad
const URLS = [
  process.env.RPC_URL_PRIMARY,
  process.env.RPC_URL_SECONDARY,
  process.env.RPC_URL_TERTIARY,
  process.env.RPC_URL_QUATERNARY
].filter(Boolean);

// Resolver A
let A_ADDR = process.env.A_ADDRESS;
if (process.env.PRIV_KEY_A) {
  try { A_ADDR = new ethers.Wallet(process.env.PRIV_KEY_A).address; } catch (_) {}
}

if (URLS.length === 0) {
  console.error('Faltan RPC_URL_* en .env'); process.exit(1);
}
if (!MOTHER_PRIV || !A_ADDR) {
  console.error('Faltan PRIVATE_KEY_CUENTA_MADRE y PRIV_KEY_A (o A_ADDRESS) en .env'); process.exit(1);
}

// Parámetros de goteo
const DRIP_AMOUNT_ETH = process.env.DRIP_A_AMOUNT_ETH || process.env.DRIP_AMOUNT_ETH || '0.00020';
const MIN_TARGET_ETH  = process.env.MIN_A_TARGET_ETH  || process.env.MIN_TARGET_ETH  || '0.00005';

// Reintentos/backoff
const MAX_RETRIES   = parseInt(process.env.DRIP_RPC_MAX_RETRIES || '5', 10);
const BASE_DELAY_MS = parseInt(process.env.DRIP_RETRY_BASE_MS   || '600', 10);

const sleep = (ms) => new Promise(r => setTimeout(r, ms));
function isRateLimit(e) {
  const msg  = (e && (e.message || e.shortMessage)) || '';
  const code = e && (e.code || (e.info && e.info.error && e.info.error.code));
  return msg.includes('Too Many Requests') || code === -32005 || code === 'SERVER_ERROR';
}

// Selección de provider que confirme CHAIN_ID
async function pickProvider() {
  console.log('Sondeando RPCs (esperado chainId =', CHAIN_ID, ')');
  for (const u of URLS) {
    try {
      const p = new ethers.JsonRpcProvider(u);
      const net = await p.getNetwork();
      console.log(`  → ${u}   chainId=${net.chainId}`);
      if (Number(net.chainId) === CHAIN_ID) return p;
      console.warn(`     descarto: chainId inesperado (esperado ${CHAIN_ID})`);
    } catch (e) {
      console.warn(`  ! fallo sonda ${u}: ${e?.message || e}`);
    }
  }
  throw new Error('No hay RPC operativo que reporte el CHAIN_ID esperado');
}

(async () => {
  const provider = await pickProvider();
  const mother   = new ethers.Wallet(MOTHER_PRIV, provider);

  async function getBalanceRetry(addr) {
    let attempt = 0;
    for (;;) {
      try { return await provider.getBalance(addr); }
      catch (e) {
        attempt++;
        if (attempt > MAX_RETRIES || !isRateLimit(e)) throw e;
        const backoff = BASE_DELAY_MS * Math.pow(2, attempt - 1);
        console.warn(`Rate-limit getBalance (intento ${attempt}/${MAX_RETRIES}). Reintento en ${backoff} ms`);
        await sleep(backoff);
      }
    }
  }

  async function sendTxRetry(txReq) {
    let attempt = 0;
    for (;;) {
      try { return await mother.sendTransaction(txReq); }
      catch (e) {
        attempt++;
        if (attempt > MAX_RETRIES || !isRateLimit(e)) throw e;
        const backoff = BASE_DELAY_MS * Math.pow(2, attempt - 1);
        console.warn(`Rate-limit sendTransaction (intento ${attempt}/${MAX_RETRIES}). Reintento en ${backoff} ms`);
        await sleep(backoff);
      }
    }
  }

  console.log('Red esperada (CHAIN_ID):', CHAIN_ID);
  console.log('Madre:', mother.address, '(MOTHER_ADDRESS=', process.env.MOTHER_ADDRESS || 'N/A', ')');
  console.log('A    :', A_ADDR);

  const balA0 = await getBalanceRetry(A_ADDR);
  const balM0 = await getBalanceRetry(mother.address);

  console.log('Balance madre (ETH) antes:', ethers.formatEther(balM0));
  console.log('Balance A (ETH) antes   :', ethers.formatEther(balA0));

  const need   = ethers.parseEther(MIN_TARGET_ETH);
  if (balA0 >= need) {
    console.log('A ya tiene ETH suficiente para approve(); no se envía nada.');
    return;
  }

  const amount = ethers.parseEther(DRIP_AMOUNT_ETH);
  if (balM0 <= amount) { console.error('Saldo insuficiente en madre para goteo'); process.exit(1); }

  const tx = await sendTxRetry({ to: A_ADDR, value: amount });
  console.log('Drip tx hash:', tx.hash);
  await tx.wait(1);

  const balA1 = await getBalanceRetry(A_ADDR);
  const balM1 = await getBalanceRetry(mother.address);

  console.log('Balance madre (ETH) después:', ethers.formatEther(balM1));
  console.log('Balance A (ETH) después   :', ethers.formatEther(balA1));
  console.log('Listo: A tiene ETH para firmar approve().');
})().catch(e => {
  console.error('ERROR drip-to-a:', e?.message || e);
  process.exit(1);
});
