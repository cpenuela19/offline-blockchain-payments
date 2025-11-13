// backend/refund-eth-to-mother.js
// Devuelve a la madre casi todo el ETH de A, dejando un pequeño colchón para evitar fallos por fee.
// Requisitos en .env: RPC_URL_*, CHAIN_ID, PRIV_KEY_A, MOTHER_ADDRESS.

require('dotenv').config();

const { ethers } = require('ethers');

const RPC = process.env.RPC_URL_PRIMARY || process.env.RPC_URL_SECONDARY;
const CHAIN_ID = parseInt(process.env.CHAIN_ID || '11155111', 10);
const A_PRIV = process.env.PRIV_KEY_A;
const MOTHER_ADDR = process.env.MOTHER_ADDRESS;

if (!RPC || !A_PRIV || !MOTHER_ADDR) {
  console.error('Faltan variables en .env: RPC_URL_*, PRIV_KEY_A, MOTHER_ADDRESS.');
  process.exit(1);
}

const KEEP_ETH = process.env.REFUND_KEEP_ETH || '0.000005'; // 5e-6 ETH

(async () => {
  const provider = new ethers.JsonRpcProvider(RPC, CHAIN_ID);
  const A = new ethers.Wallet(A_PRIV, provider);
  const balA0 = await provider.getBalance(A.address);

  console.log('A:', A.address);
  console.log('Madre:', MOTHER_ADDR);
  console.log('Balance A antes (ETH):', ethers.formatEther(balA0));

  const keep = ethers.parseEther(KEEP_ETH);
  if (balA0 <= keep) {
    console.log('Nada para devolver (balance <= colchón).');
    return;
  }

  const value = balA0 - keep;
  const tx = await A.sendTransaction({ to: MOTHER_ADDR, value });
  console.log('Refund tx hash:', tx.hash);
  await tx.wait(1);

  const balA1 = await provider.getBalance(A.address);
  console.log('Balance A después (ETH):', ethers.formatEther(balA1));
  console.log('Listo: devuelto a la madre.');
})().catch(e => {
  console.error('ERROR refund-eth-to-mother:', e?.message || e);
  process.exit(1);
});

