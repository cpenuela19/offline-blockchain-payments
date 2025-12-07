const { ethers } = require('ethers');
const path = require('path');
require('dotenv').config({ path: path.resolve(__dirname, '../.env') });
require('dotenv').config({ path: path.resolve(__dirname, '.env') });

const PRIVATE_KEY = process.env.PRIVATE_KEY_CUENTA_MADRE;
const CONTRACT_ADDRESS = process.env.CONTRACT_ADDRESS_AP;
const RPC_URL = process.env.RPC_URL_PRIMARY || process.env.RPC_URL;

if (!PRIVATE_KEY || !CONTRACT_ADDRESS || !RPC_URL) {
  console.error('❌ Faltan variables de entorno');
  process.exit(1);
}

const provider = new ethers.JsonRpcProvider(RPC_URL);
const wallet = new ethers.Wallet(PRIVATE_KEY, provider);

const ERC20_ABI = [
  'function transfer(address to, uint256 amount) external returns (bool)',
  'function balanceOf(address account) external view returns (uint256)',
  'function decimals() external view returns (uint8)'
];

const contract = new ethers.Contract(CONTRACT_ADDRESS, ERC20_ABI, wallet);

async function sendTokens() {
  try {
    // Dirección destino y cantidad
    const recipientAddress = process.argv[2];
    const amount = process.argv[3] || '1000'; // Por defecto 1000 AP
    
    if (!recipientAddress) {
      console.error('❌ Uso: node send-tokens.js <address> [cantidad]');
      console.error('   Ejemplo: node send-tokens.js 0xe4a20ea8bb8f49bea82f6d03b8385f7e4ae053f9 1000');
      process.exit(1);
    }
    
    // Validar dirección
    if (!recipientAddress.match(/^0x[0-9a-fA-F]{40}$/)) {
      console.error('❌ Dirección inválida');
      process.exit(1);
    }
    
    console.log('🚀 Preparando transferencia de AgroPuntos...\n');
    console.log('📍 Desde (Cuenta Madre):', wallet.address);
    console.log('📍 Hacia (Tu Wallet):', recipientAddress);
    console.log('💰 Cantidad:', amount, 'AP\n');
    
    // Obtener decimals y balance actual
    const [decimals, balanceBefore] = await Promise.all([
      contract.decimals(),
      contract.balanceOf(recipientAddress)
    ]);
    
    const balanceBeforeFormatted = ethers.formatUnits(balanceBefore, decimals);
    console.log('💼 Balance actual del destinatario:', balanceBeforeFormatted, 'AP\n');
    
    // Convertir cantidad a wei
    const amountWei = ethers.parseUnits(amount, decimals);
    
    console.log('📤 Enviando transacción...');
    const tx = await contract.transfer(recipientAddress, amountWei);
    console.log('✅ Transacción enviada:', tx.hash);
    console.log('🔗 Ver en Etherscan:', `https://sepolia.etherscan.io/tx/${tx.hash}\n`);
    
    console.log('⏳ Esperando confirmación...');
    const receipt = await tx.wait(1);
    
    if (receipt.status === 1) {
      console.log('✅ ¡Transacción confirmada!\n');
      
      // Verificar nuevo balance
      const balanceAfter = await contract.balanceOf(recipientAddress);
      const balanceAfterFormatted = ethers.formatUnits(balanceAfter, decimals);
      
      console.log('📊 Balance actualizado del destinatario:', balanceAfterFormatted, 'AP');
      console.log('➕ Incremento:', (parseFloat(balanceAfterFormatted) - parseFloat(balanceBeforeFormatted)).toFixed(2), 'AP\n');
      
      console.log('🎉 ¡Transferencia completada exitosamente!');
      console.log('💡 Abre tu app y toca el botón de refrescar para ver el nuevo balance');
    } else {
      console.error('❌ Transacción falló');
    }
  } catch (error) {
    console.error('❌ Error:', error.message);
    if (error.reason) {
      console.error('   Razón:', error.reason);
    }
  }
}

sendTokens();

