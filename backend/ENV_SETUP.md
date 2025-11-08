# Configuración de Variables de Entorno

Crea un archivo `.env` en la carpeta `backend/` con el siguiente contenido:

```env
# RPC URL de la testnet (Sepolia)
RPC_URL=https://sepolia.infura.io/v3/YOUR_INFURA_KEY
# O usar RPC público: https://rpc.sepolia.org

# Chain ID (Sepolia = 11155111)
CHAIN_ID=11155111

# Private key de la cuenta madre (NUNCA compartir en producción)
# Debe empezar con 0x
PRIVATE_KEY_CUENTA_MADRE=0x...

# Dirección del contrato ERC-20 AgroPuntos
# Debe empezar con 0x
CONTRACT_ADDRESS_AP=0x...

# Confirmaciones antes de considerar la tx confirmada (1-2 para testnet)
CONFIRMATIONS=1

# Puerto del servidor
PORT=3000

# Rate limit (requests por minuto por IP)
RATE_LIMIT_WINDOW_MS=60000
RATE_LIMIT_MAX_REQUESTS=30
```

## Notas importantes:

1. **PRIVATE_KEY_CUENTA_MADRE**: Esta clave debe tener:
   - Tokens AP en el contrato
   - ETH suficiente para gas fees en Sepolia

2. **CONTRACT_ADDRESS_AP**: Debe ser un contrato ERC-20 válido con:
   - Método `transfer(address, uint256)`
   - Método `balanceOf(address)`
   - Método `decimals()` (típicamente retorna 18)

3. **RPC_URL**: Para Sepolia puedes usar:
   - Infura: `https://sepolia.infura.io/v3/YOUR_KEY`
   - Alchemy: `https://eth-sepolia.g.alchemy.com/v2/YOUR_KEY`
   - RPC público: `https://rpc.sepolia.org` (puede ser lento)

## Para desarrollo local con Android Emulator:

Si estás usando el emulador de Android, el backend debe estar accesible en `10.0.2.2:3000` (configurado en `ApiClient.kt`).

Para dispositivos físicos, usa la IP local de tu máquina (ej: `192.168.1.100:3000`).

