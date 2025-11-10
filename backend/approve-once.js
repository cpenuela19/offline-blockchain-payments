// approve-once.js — Autoriza a la madre para mover AP desde A (UNA SOLA VEZ).
// ███ ATENCIÓN ███
// - Edita .env y agrega:
//     PRIV_KEY_A=0x<clave_privada_de_test1>        // REQUIRED
//     MOTHER_ADDRESS=0x<direccion_0x_de_la_madre>  // REQUIRED (SOLO DIRECCIÓN, NO LA CLAVE)
// - A (test1) necesita un poco de ETH Sepolia para enviar este approve (faucet).
// - NO subas .env a git.

require('dotenv').config();
const { ethers } = require('ethers');

const RPC = process.env.RPC_URL_PRIMARY || process.env.RPC_URL_SECONDARY || process.env.RPC_URL;
const CHAIN_ID = parseInt(process.env.CHAIN_ID || '11155111', 10);

const A_PRIV = process.env.PRIV_KEY_A;            // REQUIRED: PÓNLO EN .env
const TOKEN  = process.env.CONTRACT_ADDRESS_AP;   // ya existe en .env
const MOTHER = process.env.MOTHER_ADDRESS;        // REQUIRED: PÓNLA EN .env (dirección 0x)

if (!A_PRIV || !TOKEN || !MOTHER) {
  console.error('Faltan PRIV_KEY_A, CONTRACT_ADDRESS_AP o MOTHER_ADDRESS en .env');
  process.exit(1);
}

const ABI = [
  "function approve(address spender, uint256 value) external returns (bool)",
  "function allowance(address owner, address spender) external view returns (uint256)"
];

(async () => {
  const provider = new ethers.JsonRpcProvider(RPC, CHAIN_ID);
  const A = new ethers.Wallet(A_PRIV, provider);
  const ap = new ethers.Contract(TOKEN, ABI, A);

  console.log('Cuenta A (owner):', A.address);
  console.log('Autorizando a la madre (spender):', MOTHER);

  const antes = await ap.allowance(A.address, MOTHER);
  console.log('Allowance antes:', antes.toString());

  const tx = await ap.approve(MOTHER, ethers.MaxUint256);
  console.log('Transacción approve:', tx.hash);
  await tx.wait(1);

  const despues = await ap.allowance(A.address, MOTHER);
  console.log('Allowance después:', despues.toString());
  console.log('Listo: la madre podrá usar transferFrom(A → B) pagando el gas.');
})();

