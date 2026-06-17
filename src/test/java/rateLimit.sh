# Fire 25 POST requests
for i in $(seq 1 25); do
  STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST http://54.20.64.179:8080/api/v1/links \
    -H "Content-Type: application/json" \
    -d '{"destination":"https://example.com"}')
  echo "Request $i: $STATUS"
done