# Configuración del Contrato ERC-20 AgroPuntos

## Opción 1: Usar un contrato ERC-20 existente

Si ya tienes un contrato ERC-20 desplegado en Sepolia:

1. Copia la dirección del contrato
2. Agrega la dirección en `.env` como `CONTRACT_ADDRESS_AP`
3. El contrato debe tener los métodos estándar ERC-20:
   - `transfer(address to, uint256 amount)`
   - `balanceOf(address account)`
   - `decimals()` (debe retornar 18)

## Opción 2: Desplegar nuevo contrato

### Contrato ERC-20 simple (AgroPuntos.sol)

```solidity
// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@openzeppelin/contracts/token/ERC20/ERC20.sol";

contract AgroPuntos is ERC20 {
    constructor() ERC20("AgroPuntos", "AP") {
        // Mint inicial a la cuenta madre (ajustar cantidad)
        _mint(msg.sender, 1000000 * 10**decimals()); // 1M AP
    }
}
```

### Deploy con Hardhat

1. Instalar dependencias:
```bash
npm install --save-dev hardhat @openzeppelin/contracts
npx hardhat init
```

2. Crear `contracts/AgroPuntos.sol` con el código arriba

3. Configurar `hardhat.config.js`:
```javascript
require("@nomicfoundation/hardhat-toolbox");

module.exports = {
  solidity: "0.8.20",
  networks: {
    sepolia: {
      url: process.env.RPC_URL,
      accounts: [process.env.PRIVATE_KEY_CUENTA_MADRE]
    }
  }
};
```

4. Deploy:
```bash
npx hardhat run scripts/deploy.js --network sepolia
```

5. Copiar la dirección del contrato desplegado a `.env`

### Deploy con Remix

1. Ir a https://remix.ethereum.org
2. Crear nuevo archivo `AgroPuntos.sol`
3. Compilar con Solidity 0.8.20
4. Deploy en Sepolia usando MetaMask
5. Copiar dirección del contrato

## Verificación

Después de deployar, verifica que:
1. La cuenta madre tiene tokens AP
2. El contrato responde a `balanceOf(cuenta_madre)`
3. Puedes hacer `transfer` desde la cuenta madre

## Notas

- El contrato debe estar en la misma red que configuraste en `CHAIN_ID`
- Asegúrate de tener suficientes tokens AP en la cuenta madre para las transferencias
- En producción, considera implementar mint/burn controlado

