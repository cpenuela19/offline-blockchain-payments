# Mejoras de Seguridad en Endpoint de Settle

## Contexto del Proyecto

Este proyecto implementa un sistema de **pagos offline con blockchain** que permite realizar transacciones de AgroPuntos (tokens ERC-20) sin conexión a internet mediante códigos QR y Bluetooth Low Energy (BLE).

El componente crítico de este sistema es el **endpoint de settle** (`POST /v1/vouchers/settle`), que procesa y valida las transacciones offline una vez que los dispositivos recuperan conexión.

---

## Objetivo del Endpoint de Settle

El endpoint `/v1/vouchers/settle` tiene como objetivo:

1. **Recibir vouchers de transacciones offline** generados entre dos usuarios (comprador y vendedor)
2. **Validar las firmas criptográficas** de ambas partes para garantizar autenticidad
3. **Verificar la integridad del payload** mediante canonicalización
4. **Encolar la transacción** para procesamiento on-chain (patrón outbox)
5. **Ejecutar la transferencia real** de tokens en la blockchain usando `transferFrom()`

### Flujo de Pago Offline

```
Comprador (offline)  <--BLE-->  Vendedor (offline)
      |                              |
      | Firma voucher               | Firma voucher
      | (buyer_sig)                 | (seller_sig)
      |                              |
      v                              v
    [Voucher firmado por ambos]
              |
              | Cuando hay internet
              v
    POST /v1/vouchers/settle
              |
              v
    [Validaciones de seguridad]
              |
              v
    [Transferencia on-chain]
```

---

## Estado Inicial: Verificación de Firmas

### ¿Qué funcionaba antes?

El endpoint **SÍ verificaba las firmas criptográficas** de forma correcta:

```javascript
// Código existente (líneas 789-792 de server.js)
const okSeller = verifySignature(canonical, seller_sig, sellerLower);
const okBuyer = verifySignature(canonical, buyer_sig, buyerLower);
if (!okSeller || !okBuyer) {
  return res.status(422).json({ 
    error_code: 'INVALID_SIGNATURE', 
    message: 'seller_sig or buyer_sig invalid' 
  });
}
```

### Proceso de Verificación (Ya Implementado)

1. **Canonicalización del payload:**
   ```javascript
   const base = {
     offer_id,
     amount_ap: String(amount_ap),
     asset,
     expiry: Number(expiry),
     seller_address: sellerLower,
     buyer_address: buyerLower
   };
   const canonical = canonicalizePaymentBase(base);
   // Resultado: JSON ordenado alfabéticamente
   ```

2. **Verificación criptográfica:**
   ```javascript
   function verifySignature(canonicalString, signature, expectedAddress) {
     try {
       const msgHash = ethers.hashMessage(canonicalString);
       const recovered = ethers.recoverAddress(msgHash, signature);
       return recovered.toLowerCase() === String(expectedAddress).toLowerCase();
     } catch (_e) {
       return false;
     }
   }
   ```

### Estándar Criptográfico Utilizado

- **ECDSA secp256k1:** Algoritmo de firma de curva elíptica (mismo que Ethereum)
- **EIP-191:** Ethereum Signed Message Standard
  - Formato: `"\x19Ethereum Signed Message:\n" + len(message) + message`
  - Previene ataques de replay entre diferentes contextos

### Por Qué Era Válido

El sistema **SÍ era criptográficamente seguro** en términos de verificación de firmas:

✅ **Autenticidad garantizada:** Las firmas ECDSA son prácticamente imposibles de falsificar sin la clave privada

✅ **Integridad del mensaje:** Cualquier modificación del payload invalida la firma

✅ **No repudio:** Solo el poseedor de la clave privada pudo firmar el mensaje

✅ **Canonicalización correcta:** El payload se serializa de forma determinística, evitando problemas de orden de campos

---

## Vulnerabilidades Identificadas

Sin embargo, el endpoint **carecía de validaciones de seguridad adicionales** que son críticas en un sistema de producción:

### 1. Auto-Transferencias (Sin Validar)

**Problema:** Un usuario podía crear vouchers donde buyer === seller

```javascript
// Escenario de ataque
buyer_address:  "0xe4a20ea8bb8f49bea82f6d03b8385f7e4ae053f9"
seller_address: "0xe4a20ea8bb8f49bea82f6d03b8385f7e4ae053f9" // ¡Mismo!
```

**Impacto:**
- Inflación artificial de estadísticas de transacciones
- Posible manipulación de incentivos/comisiones
- Confusión en auditorías

### 2. Usuarios No Registrados (Sin Verificar)

**Problema:** El sistema aceptaba transacciones de/hacia direcciones no registradas

```javascript
// Escenario de ataque
buyer_address:  "0xABCDEF..." // ¡No está en la tabla users!
seller_address: "0x123456..." // ¡Dirección aleatoria!
```

**Impacto:**
- Intentos de drenar la cuenta madre hacia wallets maliciosos
- Procesamiento de transacciones ilegítimas
- Abuso del sistema de faucet/outbox

### 3. Rate Limiting Insuficiente

**Problema:** El settle endpoint usaba el mismo rate limit que otros endpoints (30 req/min)

**Impacto:**
- Vulnerable a ataques de fuerza bruta sobre firmas
- Posible DoS (Denial of Service)
- Spam de intentos de settle maliciosos

### 4. Logs de Auditoría Limitados

**Problema:** Mensajes de error genéricos sin logs detallados

```javascript
// Antes
if (!okSeller || !okBuyer) {
  return res.status(422).json({ 
    error_code: 'INVALID_SIGNATURE', 
    message: 'seller_sig or buyer_sig invalid' 
  });
}
```

**Impacto:**
- Difícil rastrear intentos de ataque
- No se distinguía qué firma falló (buyer vs seller)
- Imposible hacer análisis forense de incidentes

---

## Mejoras Implementadas

### 1. Validación Anti Auto-Transferencia

```javascript
// NUEVO: Validar que buyer ≠ seller
if (buyerLower === sellerLower) {
  console.error(`🚨 [SETTLE] ATAQUE DETECTADO: Auto-transferencia - ${buyerLower}`);
  return res.status(400).json({ 
    error_code: 'SAME_ADDRESS', 
    message: 'buyer y seller no pueden ser la misma dirección' 
  });
}
```

**Beneficio:**
- Bloquea intentos de auto-transferencia
- Logs de auditoría inmediatos
- Código de error específico

### 2. Verificación de Usuarios Registrados

```javascript
// NUEVO: Verificar que ambas addresses existan en la BD
const [buyerExists, sellerExists] = await Promise.all([
  new Promise((resolve) => {
    db.get('SELECT address FROM users WHERE address = ?', [buyerLower], (err, row) => {
      resolve(!err && !!row);
    });
  }),
  new Promise((resolve) => {
    db.get('SELECT address FROM users WHERE address = ?', [sellerLower], (err, row) => {
      resolve(!err && !!row);
    });
  })
]);

if (!buyerExists) {
  console.error(`🚨 [SETTLE] ATAQUE DETECTADO: Buyer no registrado - ${buyerLower}`);
  return res.status(403).json({ 
    error_code: 'BUYER_NOT_REGISTERED', 
    message: 'Buyer address no está registrado en el sistema' 
  });
}

if (!sellerExists) {
  console.error(`🚨 [SETTLE] ATAQUE DETECTADO: Seller no registrado - ${sellerLower}`);
  return res.status(403).json({ 
    error_code: 'SELLER_NOT_REGISTERED', 
    message: 'Seller address no está registrado en el sistema' 
  });
}
```

**Beneficio:**
- Solo usuarios legítimos pueden participar en transacciones
- Verificación en paralelo (Promise.all) para mejor performance
- Previene ataques con direcciones aleatorias

### 3. Rate Limiting Estricto

```javascript
// NUEVO: Rate limiter específico para settle
const settleLimiter = rateLimit({
  windowMs: 60000,      // 1 minuto
  max: 10,              // Máximo 10 settle requests por minuto
  message: 'Demasiados intentos de settle, intenta más tarde',
  handler: (req, res) => {
    console.warn(`🚨 [RATE_LIMIT] IP bloqueada temporalmente en settle: ${req.ip}`);
    res.status(429).json({
      error_code: 'RATE_LIMIT_EXCEEDED',
      message: 'Demasiados intentos de settle. Espera 1 minuto.'
    });
  }
});

// Aplicar al endpoint
app.post('/v1/vouchers/settle', settleLimiter, async (req, res) => { ... });
```

**Beneficio:**
- Límite más estricto que otros endpoints (10 vs 30 req/min)
- Logs de IPs bloqueadas
- Previene fuerza bruta y DoS

### 4. Logs de Auditoría Detallados

```javascript
// NUEVO: Logs específicos por tipo de fallo
const okSeller = verifySignature(canonical, seller_sig, sellerLower);
if (!okSeller) {
  console.error(`🚨 [SETTLE] FIRMA INVÁLIDA: Seller signature falló - ${sellerLower}`);
  console.error(`   offer_id: ${offer_id}`);
  console.error(`   canonical: ${canonical}`);
  console.error(`   seller_sig: ${seller_sig}`);
  return res.status(422).json({ 
    error_code: 'INVALID_SELLER_SIGNATURE', 
    message: 'Firma del vendedor inválida' 
  });
}

const okBuyer = verifySignature(canonical, buyer_sig, buyerLower);
if (!okBuyer) {
  console.error(`🚨 [SETTLE] FIRMA INVÁLIDA: Buyer signature falló - ${buyerLower}`);
  console.error(`   offer_id: ${offer_id}`);
  console.error(`   canonical: ${canonical}`);
  console.error(`   buyer_sig: ${buyer_sig}`);
  return res.status(422).json({ 
    error_code: 'INVALID_BUYER_SIGNATURE', 
    message: 'Firma del comprador inválida' 
  });
}

// NUEVO: Confirmación de éxito
console.log(`✅ [SETTLE] Firmas verificadas exitosamente para ${offer_id}`);
console.log(`   Buyer: ${buyerLower}`);
console.log(`   Seller: ${sellerLower}`);
console.log(`   Amount: ${amount_ap} AP`);
```

**Beneficio:**
- Distingue qué firma falló específicamente
- Incluye contexto completo para debugging
- Facilita análisis forense de incidentes
- Códigos de error granulares

### 5. Validaciones Existentes Mejoradas con Logs

```javascript
// MEJORADO: Todas las validaciones ahora tienen logs
if (!offer_id || !amount_ap || ...) {
  console.warn(`🔒 [SETTLE] Campos faltantes en request`);
  return res.status(400).json({ ... });
}

if (!/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(offer_id)) {
  console.warn(`🔒 [SETTLE] offer_id inválido: ${offer_id}`);
  return res.status(400).json({ ... });
}

if (Number(expiry) <= now) {
  console.warn(`🔒 [SETTLE] Voucher expirado: ${offer_id}, expiry: ${expiry}, now: ${now}`);
  return res.status(409).json({ ... });
}
```

**Beneficio:**
- Trazabilidad completa de rechazos
- Facilita debugging en desarrollo
- Permite monitoreo de intentos sospechosos

---

## Estado Final: Arquitectura de Seguridad en Capas

El endpoint de settle ahora implementa **defensa en profundidad** con 5 capas:

```
┌─────────────────────────────────────────────────────┐
│  CAPA 1: Rate Limiting Estricto                     │
│  - Máximo 10 requests/minuto                        │
│  - Logs de IPs bloqueadas                           │
└─────────────────────────────────────────────────────┘
              ↓ Request pasa
┌─────────────────────────────────────────────────────┐
│  CAPA 2: Validaciones de Formato                    │
│  - Campos requeridos                                │
│  - UUID v4 válido                                   │
│  - Asset = 'AP'                                     │
│  - Expiry no pasado                                 │
│  - Direcciones 0x válidas                           │
│  - Monto > 0                                        │
└─────────────────────────────────────────────────────┘
              ↓ Formato válido
┌─────────────────────────────────────────────────────┐
│  CAPA 3: Validaciones de Lógica de Negocio          │
│  - buyer ≠ seller                                   │
│  - buyer registrado en sistema                      │
│  - seller registrado en sistema                     │
└─────────────────────────────────────────────────────┘
              ↓ Lógica válida
┌─────────────────────────────────────────────────────┐
│  CAPA 4: Verificación Criptográfica                 │
│  - Canonicalización del payload                     │
│  - Verificación firma seller (ECDSA + EIP-191)      │
│  - Verificación firma buyer (ECDSA + EIP-191)       │
└─────────────────────────────────────────────────────┘
              ↓ Firmas válidas
┌─────────────────────────────────────────────────────┐
│  CAPA 5: Procesamiento On-Chain                     │
│  - Inserción/actualización en BD                    │
│  - Encolado en outbox                               │
│  - Ejecución de transferFrom()                      │
│  - Confirmación en blockchain                       │
└─────────────────────────────────────────────────────┘
              ↓
         [✅ ÉXITO]
```

---

## Comparativa: Antes vs. Después

| Aspecto | Estado Inicial | Estado Final |
|---------|----------------|--------------|
| **Verificación de firmas** | ✅ Implementada correctamente | ✅ Implementada + Logs detallados |
| **Estándar criptográfico** | ✅ ECDSA + EIP-191 | ✅ ECDSA + EIP-191 |
| **Canonicalización** | ✅ Determinística | ✅ Determinística |
| **Auto-transferencia** | ❌ Permitida | ✅ Bloqueada |
| **Usuarios no registrados** | ❌ Permitidos | ✅ Bloqueados |
| **Rate limiting** | ⚠️ Genérico (30/min) | ✅ Estricto (10/min) |
| **Logs de auditoría** | ⚠️ Básicos | ✅ Detallados |
| **Códigos de error** | ⚠️ Genéricos | ✅ Específicos |
| **Trazabilidad** | ⚠️ Limitada | ✅ Completa |

---

## Vectores de Ataque: Mitigación

### Ataque 1: Firmas Falsificadas

**Antes:** ✅ Bloqueado (verificación criptográfica)  
**Después:** ✅ Bloqueado + Logs detallados de intentos

### Ataque 2: Auto-Transferencias

**Antes:** ❌ Vulnerable (permitido)  
**Después:** ✅ Mitigado (validación explícita)

### Ataque 3: Direcciones No Registradas

**Antes:** ❌ Vulnerable (sin verificación)  
**Después:** ✅ Mitigado (verificación en BD)

### Ataque 4: Replay Attacks

**Antes:** ✅ Protegido (offer_id único + expiry)  
**Después:** ✅ Protegido (sin cambios)

### Ataque 5: Fuerza Bruta

**Antes:** ⚠️ Limitado (30 req/min)  
**Después:** ✅ Muy limitado (10 req/min + logs)

### Ataque 6: Denial of Service (DoS)

**Antes:** ⚠️ Posible (rate limit permisivo)  
**Después:** ✅ Mitigado (rate limit estricto + bloqueo de IPs)

---

## Ejemplos de Logs de Seguridad

### Transacción Exitosa

```
🔒 [SETTLE] Verificando firmas para 550e8400-e29b-41d4-a716-446655440000...
   Buyer: 0xe4a20ea8bb8f49bea82f6d03b8385f7e4ae053f9
   Seller: 0xdc1a640c1869993b9f7b451979652f75a1221275
   Amount: 100 AP
✅ [SETTLE] Firmas verificadas exitosamente para 550e8400-e29b-41d4-a716-446655440000
```

### Intento de Auto-Transferencia

```
🚨 [SETTLE] ATAQUE DETECTADO: Auto-transferencia - 0xe4a20ea8bb8f49bea82f6d03b8385f7e4ae053f9
```

### Usuario No Registrado

```
🚨 [SETTLE] ATAQUE DETECTADO: Buyer no registrado - 0xabcdef1234567890abcdef1234567890abcdef12
```

### Firma Inválida

```
🚨 [SETTLE] FIRMA INVÁLIDA: Buyer signature falló - 0xe4a20ea8bb8f49bea82f6d03b8385f7e4ae053f9
   offer_id: 550e8400-e29b-41d4-a716-446655440000
   canonical: {"asset":"AP","buyer_address":"0xe4a2...","expiry":1732074000,"offer_id":"550e8400-e29b-41d4-a716-446655440000","seller_address":"0xdc1a...","amount_ap":"100"}
   buyer_sig: 0x1234567890abcdef...
```

### Rate Limit Excedido

```
🚨 [RATE_LIMIT] IP bloqueada temporalmente en settle: 192.168.10.4
```

---

## Configuración y Despliegue

### Variables de Entorno

Las mejoras utilizan la configuración existente de rate limiting:

```env
# Rate limiting general (otros endpoints)
RATE_LIMIT_WINDOW_MS=60000      # 1 minuto
RATE_LIMIT_MAX_REQUESTS=30      # 30 requests/min

# Rate limiting de settle (hardcodeado en server.js)
# settleLimiter: 10 requests/minuto
```

### Reinicio del Servidor

Después de aplicar las mejoras, reiniciar el backend:

```bash
cd backend
node server.js
```

---

## Testing Recomendado

### 1. Test de Firmas Válidas

```bash
curl -X POST http://localhost:3000/v1/vouchers/settle \
  -H "Content-Type: application/json" \
  -d '{
    "offer_id": "550e8400-e29b-41d4-a716-446655440000",
    "amount_ap": "100",
    "asset": "AP",
    "expiry": 1732074000,
    "buyer_address": "0xe4a20ea8bb8f49bea82f6d03b8385f7e4ae053f9",
    "seller_address": "0xdc1a640c1869993b9f7b451979652f75a1221275",
    "buyer_sig": "0x...",
    "seller_sig": "0x..."
  }'

# Esperado: 200 OK (si firmas correctas)
```

### 2. Test de Auto-Transferencia

```bash
curl -X POST http://localhost:3000/v1/vouchers/settle \
  -H "Content-Type: application/json" \
  -d '{
    ...
    "buyer_address": "0xe4a20ea8bb8f49bea82f6d03b8385f7e4ae053f9",
    "seller_address": "0xe4a20ea8bb8f49bea82f6d03b8385f7e4ae053f9"
  }'

# Esperado: 400 BAD REQUEST
# { "error_code": "SAME_ADDRESS", ... }
```

### 3. Test de Usuario No Registrado

```bash
curl -X POST http://localhost:3000/v1/vouchers/settle \
  -H "Content-Type: application/json" \
  -d '{
    ...
    "buyer_address": "0xABCDEF1234567890ABCDEF1234567890ABCDEF12"
  }'

# Esperado: 403 FORBIDDEN
# { "error_code": "BUYER_NOT_REGISTERED", ... }
```

### 4. Test de Rate Limiting

```bash
# Enviar 11 requests en menos de 1 minuto
for i in {1..11}; do
  curl -X POST http://localhost:3000/v1/vouchers/settle ...
done

# Esperado: Request 11 devuelve 429 TOO MANY REQUESTS
# { "error_code": "RATE_LIMIT_EXCEEDED", ... }
```

---

## Conclusión

### Situación Inicial

El sistema **YA era criptográficamente seguro** en términos de verificación de firmas ECDSA y cumplimiento del estándar EIP-191. Las firmas **SÍ se verificaban correctamente** y era **prácticamente imposible** falsificar transacciones sin las claves privadas.

### Mejoras Implementadas

Las mejoras **NO corrigieron la verificación de firmas** (que ya era correcta), sino que agregaron **capas adicionales de seguridad** para prevenir otros tipos de ataques y mejorar la auditabilidad del sistema.

### Estado Final

El endpoint de settle ahora implementa:
- ✅ **Verificación criptográfica robusta** (ya existía)
- ✅ **Validaciones de lógica de negocio** (nuevo)
- ✅ **Rate limiting estricto** (mejorado)
- ✅ **Logs de auditoría completos** (nuevo)
- ✅ **Defensa en profundidad** (arquitectura mejorada)

El sistema es ahora **significativamente más robusto** ante intentos de abuso, mantiene trazabilidad completa de eventos de seguridad, y está listo para un entorno de producción o piloto académico.

---

**Documento generado:** 20 de noviembre de 2025  
**Versión:** 1.0  
**Proyecto:** Offline Blockchain Payments - Sistema de Pagos con AgroPuntos

