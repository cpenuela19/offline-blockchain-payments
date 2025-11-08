Desde offline-blockchain-payments/backend 
npm run dev


Desde otra terminal (ejemplo de una peticion)
TS=$(date +%s)
curl -X POST http://localhost:3000/v1/vouchers \
  -H "Content-Type: application/json" \
  -d '{
    "offer_id":"test-fallback-1",
    "amount_ap":100,
    "buyer_alias":"Juan",
    "seller_alias":"0x8846f77a51371269a9e84310cc978154adbf7cf8",
    "created_at":'$TS'
  }'
