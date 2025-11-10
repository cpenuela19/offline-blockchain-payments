// backend/drip-to-a.js
// Envía una pequeña cantidad de ETH Sepolia desde la madre hacia A
// para cubrir el gas de approve(). No toca el contrato AP.
// Requisitos en .env: RPC_URL_PRIMARY/RPC_URL_SECONDARY, CHAIN_ID,
// PRIVATE_KEY_CUENTA_MADRE, PRIV_KEY_A (o A_ADDRESS), MOTHER_ADDRESS (solo para logs).

require('dotenv').config();

const { ethers } = require('ethers');

const RPC = process.env.RPC_URL_PRIMARY || process.env.RPC_URL_SECONDARY;
const CHAIN_ID = parseInt(process.env.CHAIN_ID || '11155111', 10);
const MOTHER_PRIV = process.env.PRIVATE_KEY_CUENTA_MADRE;

// Resolvemos la dirección de A a partir de su privada (preferido) o variable A_ADDRESS
let A_ADDR = process.env.A_ADDRESS;
if (process.env.PRIV_KEY_A) {
  try {
    A_ADDR = new ethers.Wallet(process.env.PRIV_KEY_A).address;
  } catch (_) { /* ignore, se validará abajo */ }
}

if (!RPC || !MOTHER_PRIV || !A_ADDR) {
  console.error('Faltan variables en .env: RPC_URL_*, PRIVATE_KEY_CUENTA_MADRE y PRIV_KEY_A (o A_ADDRESS).');
  process.exit(1);
}

// Cantidad de goteo configurable (por defecto 0.00020 ETH)
const DRIP_AMOUNT_ETH = process.env.DRIP_AMOUNT_ETH || '0.00020';

// Umbral mínimo deseado en A (si ya supera esto, se omite el envío)
const MIN_TARGET_ETH = process.env.MIN_TARGET_ETH || '0.00005';

(async () => {
  const provider = new ethers.JsonRpcProvider(RPC, CHAIN_ID);
  const mother = new ethers.Wallet(MOTHER_PRIV, provider);
  const balA0 = await provider.getBalance(A_ADDR);
  const balM0 = await provider.getBalance(mother.address);

  console.log('RPC:', RPC);
  console.log('Red (CHAIN_ID):', CHAIN_ID);
  console.log('Madre:', mother.address);
  console.log('A:', A_ADDR);
  console.log('Balance madre (ETH) antes:', ethers.formatEther(balM0));
  console.log('Balance A (ETH) antes:', ethers.formatEther(balA0));

  const need = ethers.parseEther(MIN_TARGET_ETH);
  if (balA0 >= need) {
    console.log('A ya tiene ETH suficiente para cubrir approve(); no se envía nada.');
    return;
  }

  const amount = ethers.parseEther(DRIP_AMOUNT_ETH);
  if (balM0 <= amount) {
    console.error('La madre no tiene suficiente ETH en Sepolia para goteo.');
    process.exit(1);
  }

  const tx = await mother.sendTransaction({ to: A_ADDR, value: amount });
  console.log('Drip tx hash:', tx.hash);
  await tx.wait(1);

  const balA1 = await provider.getBalance(A_ADDR);
  const balM1 = await provider.getBalance(mother.address);

  console.log('Balance madre (ETH) después:', ethers.formatEther(balM1));
  console.log('Balance A (ETH) después:', ethers.formatEther(balA1));
  console.log('Listo: A tiene ETH suficiente para firmar approve().');
})().catch(e => {
  console.error('ERROR drip-to-a:', e?.message || e);
  process.exit(1);
});

