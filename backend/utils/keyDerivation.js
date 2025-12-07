const crypto = require('crypto');
const { ethers } = require('ethers');

/**
 * Derivación de claves privadas desde seed phrases de 10 palabras.
 * 
 * IMPORTANTE:
 * - Usa PBKDF2WithHmacSHA256 (100,000 iteraciones)
 * - Salt fijo: "agropuntos-v1-salt"
 * - Debe ser IDÉNTICO a la app para que las mismas palabras generen la misma clave
 * 
 * NOTA: En el modelo True Self-Custody, el backend SOLO usa esto para VERIFICAR
 * que puede calcular la misma dirección desde las palabras (para testing/debug).
 * El backend NUNCA debe almacenar ni enviar la clave privada.
 */

const SALT = 'agropuntos-v1-salt';
const ITERATIONS = 100000;
const KEY_LENGTH = 32; // 32 bytes = 256 bits

/**
 * Deriva una clave privada desde un seed phrase de 10 palabras.
 * 
 * @param {string[]} phrase10 - Array de 10 palabras en español
 * @returns {string} - Clave privada en formato hexadecimal con prefijo 0x
 */
function derivePrivateKeyFromPhrase(phrase10) {
  // Unir las palabras con espacios
  const phraseString = phrase10.join(' ');
  
  // Derivar 32 bytes usando PBKDF2
  const keyBytes = crypto.pbkdf2Sync(
    phraseString,
    SALT,
    ITERATIONS,
    KEY_LENGTH,
    'sha256'
  );
  
  // Convertir a BigInt (clave privada)
  const privateKeyBigInt = BigInt('0x' + keyBytes.toString('hex'));
  
  // Validar que esté en el rango válido de secp256k1
  // El orden de secp256k1
  const secp256k1Order = BigInt('0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141');
  
  const validPrivateKey = privateKeyBigInt >= secp256k1Order 
    ? privateKeyBigInt % secp256k1Order 
    : privateKeyBigInt;
  
  // Convertir a hex con prefijo 0x
  return '0x' + validPrivateKey.toString(16).padStart(64, '0');
}

/**
 * Obtiene la dirección Ethereum desde una clave privada.
 * 
 * @param {string} privateKey - Clave privada en formato hexadecimal (con o sin prefijo 0x)
 * @returns {string} - Dirección Ethereum (0x...)
 */
function getAddressFromPrivateKey(privateKey) {
  const wallet = new ethers.Wallet(privateKey);
  return wallet.address;
}

/**
 * Obtiene la clave pública desde una clave privada.
 * 
 * @param {string} privateKey - Clave privada en formato hexadecimal (con o sin prefijo 0x)
 * @returns {string} - Clave pública sin comprimir (0x04...)
 */
function getPublicKeyFromPrivateKey(privateKey) {
  const wallet = new ethers.Wallet(privateKey);
  return wallet.publicKey;
}

/**
 * Verifica que una frase de 10 palabras genere consistentemente la misma clave.
 * 
 * @param {string[]} phrase10 - Array de 10 palabras
 * @returns {boolean} - true si la derivación es determinística
 */
function verifyDeterminism(phrase10) {
  const key1 = derivePrivateKeyFromPhrase(phrase10);
  const key2 = derivePrivateKeyFromPhrase(phrase10);
  return key1 === key2;
}

module.exports = {
  derivePrivateKeyFromPhrase,
  getAddressFromPrivateKey,
  getPublicKeyFromPrivateKey,
  verifyDeterminism
};

