python3 -m http.server 8080 &
while true; do
  curl -s http://localhost:8080 > /dev/null
  echo "Pinged $(date)"
  sleep 120
done
