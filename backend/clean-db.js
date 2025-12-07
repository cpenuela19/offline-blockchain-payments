/**
 * Script para limpiar las bases de datos del backend
 * Uso: node clean-db.js
 */

const fs = require('fs');
const path = require('path');

const dbFiles = ['users.db', 'vouchers.db'];

console.log('🧹 Limpiando bases de datos del backend...\n');

dbFiles.forEach(file => {
  const filePath = path.join(__dirname, file);
  
  if (fs.existsSync(filePath)) {
    try {
      fs.unlinkSync(filePath);
      console.log(`✅ Eliminado: ${file}`);
    } catch (error) {
      console.error(`❌ Error eliminando ${file}:`, error.message);
    }
  } else {
    console.log(`⚠️  No existe: ${file}`);
  }
});

console.log('\n✅ Limpieza completada. Las bases de datos se recrearán automáticamente al iniciar el servidor.');
console.log('💡 Ahora ejecuta: npm run dev\n');

