const crypto = require('crypto');

// Clave maestra desde .env
const MASTER_KEY = process.env.WALLET_MASTER_KEY;

if (!MASTER_KEY) {
  console.error('❌ ERROR: WALLET_MASTER_KEY no está definida en .env');
  process.exit(1);
}

// Validar que la clave tenga 32 bytes (256 bits) para AES-256
// Si viene como hex string, debe tener 64 caracteres
// Si viene como base64, debe tener 44 caracteres
// Si viene como texto plano, derivamos un hash de 32 bytes
let masterKeyBuffer;
if (MASTER_KEY.length === 64 && /^[0-9a-fA-F]+$/.test(MASTER_KEY)) {
  // Hex string de 64 caracteres = 32 bytes
  masterKeyBuffer = Buffer.from(MASTER_KEY, 'hex');
} else if (MASTER_KEY.length === 44) {
  // Base64 de 44 caracteres = 32 bytes
  masterKeyBuffer = Buffer.from(MASTER_KEY, 'base64');
} else {
  // Derivar clave de 32 bytes usando SHA-256
  masterKeyBuffer = crypto.createHash('sha256').update(MASTER_KEY).digest();
}

if (masterKeyBuffer.length !== 32) {
  console.error('❌ ERROR: WALLET_MASTER_KEY debe derivar a 32 bytes (256 bits)');
  process.exit(1);
}

const ALGORITHM = 'aes-256-gcm';
const IV_LENGTH = 16; // 16 bytes para GCM
const AUTH_TAG_LENGTH = 16; // 16 bytes para el tag de autenticación

/**
 * Cifra una clave privada (hex string) usando AES-256-GCM
 * @param {string} plainHex - Clave privada en formato hex (con o sin prefijo 0x)
 * @returns {string} - Clave cifrada en formato base64
 */
function encryptPrivateKey(plainHex) {
  try {
    // Normalizar: remover 0x si existe
    const cleanHex = plainHex.startsWith('0x') ? plainHex.slice(2) : plainHex;
    
    // Validar que sea hex válido
    if (!/^[0-9a-fA-F]+$/.test(cleanHex)) {
      throw new Error('Invalid hex string');
    }
    
    // Convertir a buffer
    const plainBuffer = Buffer.from(cleanHex, 'hex');
    
    // Generar IV aleatorio
    const iv = crypto.randomBytes(IV_LENGTH);
    
    // Crear cipher
    const cipher = crypto.createCipheriv(ALGORITHM, masterKeyBuffer, iv);
    
    // Cifrar
    const encrypted = Buffer.concat([
      cipher.update(plainBuffer),
      cipher.final()
    ]);
    
    // Obtener auth tag
    const authTag = cipher.getAuthTag();
    
    // Combinar: IV + authTag + encrypted (todo en base64)
    const combined = Buffer.concat([iv, authTag, encrypted]);
    
    return combined.toString('base64');
  } catch (error) {
    console.error('Error cifrando clave privada:', error);
    throw new Error(`Encryption failed: ${error.message}`);
  }
}

/**
 * Descifra una clave privada cifrada
 * @param {string} cipherText - Clave cifrada en formato base64
 * @returns {string} - Clave privada en formato hex con prefijo 0x
 */
function decryptPrivateKey(cipherText) {
  try {
    // Decodificar base64
    const combined = Buffer.from(cipherText, 'base64');
    
    // Extraer componentes
    const iv = combined.slice(0, IV_LENGTH);
    const authTag = combined.slice(IV_LENGTH, IV_LENGTH + AUTH_TAG_LENGTH);
    const encrypted = combined.slice(IV_LENGTH + AUTH_TAG_LENGTH);
    
    // Crear decipher
    const decipher = crypto.createDecipheriv(ALGORITHM, masterKeyBuffer, iv);
    decipher.setAuthTag(authTag);
    
    // Descifrar
    const decrypted = Buffer.concat([
      decipher.update(encrypted),
      decipher.final()
    ]);
    
    // Convertir a hex con prefijo 0x
    return '0x' + decrypted.toString('hex');
  } catch (error) {
    console.error('Error descifrando clave privada:', error);
    throw new Error(`Decryption failed: ${error.message}`);
  }
}

module.exports = {
  encryptPrivateKey,
  decryptPrivateKey
};

