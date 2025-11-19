const crypto = require('crypto');

/**
 * Genera un token de sesión aleatorio
 * @returns {string} - Token de sesión en formato base64
 */
function generateSessionToken() {
  // Generar 32 bytes aleatorios y codificar en base64
  return crypto.randomBytes(32).toString('base64');
}

module.exports = {
  generateSessionToken
};

