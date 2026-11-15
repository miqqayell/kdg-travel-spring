#!/bin/bash

set -euo pipefail

DOMAIN="${DOMAIN:-int4-team21.duckdns.org}"
DUCKDNS_SUBDOMAIN="${DUCKDNS_SUBDOMAIN:-int4-team21}"

echo "========================================"
echo "1. DNS and Infrastructure Setup"
echo "========================================"
echo "Updating DuckDNS..."
curl -s "https://www.duckdns.org/update?domains=$DUCKDNS_SUBDOMAIN&token=$DUCKDNS_TOKEN&ip=$VM_IP"
echo



echo "Waiting for DNS propagation..."
DNS_OK=false
for i in $(seq 1 60); do
  RESOLVED_IP=$(getent hosts "$DOMAIN" | awk '{print $1}' | head -n1 || true)

  echo "DNS check attempt $i: $DOMAIN resolves to $RESOLVED_IP"

  if [ "$RESOLVED_IP" = "$VM_IP" ]; then
    echo "DNS propagated successfully"
    DNS_OK=true
    break
  fi

  sleep 10
done

if [ "$DNS_OK" != "true" ]; then
  echo "ERROR: DNS did not propagate correctly."
  echo "Expected IP: $VM_IP"
  echo "Resolved IP: ${RESOLVED_IP:-not resolved}"
  exit 1
fi

echo "DuckDNS resolves to:"
getent hosts "$DOMAIN"


echo "Setting up VM dependencies..."
scp scripts/setup-vm.sh "$VM_USER@$VM_IP:/home/$VM_USER/setup-vm.sh"
ssh "$VM_USER@$VM_IP" "chmod +x /home/$VM_USER/setup-vm.sh && sudo /home/$VM_USER/setup-vm.sh"

echo "Uploading new application JAR..."
scp build/libs/*.jar "$VM_USER@$VM_IP:/home/$VM_USER/app.jar"

echo "========================================"
echo "2. Blue-Green Deployment"
echo "========================================"

echo "Determining active environment..."
ACTIVE_ENV=$(ssh "$VM_USER@$VM_IP" "sudo docker ps --format '{{.Names}}' | grep myapp-" || echo "")

if [[ "$ACTIVE_ENV" == *"myapp-blue"* ]]; then
    NEW_ENV="myapp-green"
    NEW_PORT="8081"
    OLD_ENV="myapp-blue"
else
    NEW_ENV="myapp-blue"
    NEW_PORT="8080"
    OLD_ENV="myapp-green"
fi

echo "Deploying new version to $NEW_ENV on port $NEW_PORT..."

# Remove failed or leftover container with the same name.
# This prevents Docker name conflicts during the next deployment.
ssh "$VM_USER@$VM_IP" "sudo docker rm -f $NEW_ENV || true"

ssh "$VM_USER@$VM_IP" \
  "sudo docker run -d --restart unless-stopped \
  -p 127.0.0.1:$NEW_PORT:8080 \
  --name $NEW_ENV \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://$DB_IP:5432/$DB_NAME \
  -e SPRING_DATASOURCE_USERNAME=$DB_USER \
  -e SPRING_DATASOURCE_PASSWORD=$DB_PASSWORD \
  -v /home/$VM_USER/app.jar:/app.jar \
  eclipse-temurin:21-jdk java -jar /app.jar"


echo "Waiting for the Spring Boot app to initialize on port $NEW_PORT..."

ssh "$VM_USER@$VM_IP" "
  for i in \$(seq 1 30); do
    STATUS=\$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:$NEW_PORT || true)

    echo \"Health check attempt \$i: HTTP \$STATUS\"

    if echo \"\$STATUS\" | grep -qE '200|302|401|403|404'; then
      echo 'Application is healthy.'
      exit 0
    fi

    sleep 5
  done

  echo 'ERROR: Application did not become healthy in time.'
  echo 'New container logs:'
  sudo docker logs $NEW_ENV || true
  sudo docker rm -f $NEW_ENV || true
  exit 1
"

echo "New environment ($NEW_ENV) is healthy and running!"

echo "========================================"
echo "3. Nginx Traffic Switch & Cleanup"
echo "========================================"
echo "Uploading routing scripts..."
scp scripts/nginx.sh scripts/https.sh "$VM_USER@$VM_IP:/home/$VM_USER/"
ssh "$VM_USER@$VM_IP" "chmod +x /home/$VM_USER/nginx.sh /home/$VM_USER/https.sh"




echo "Configuring Nginx reverse proxy to port $NEW_PORT..."
ssh "$VM_USER@$VM_IP" "sudo /home/$VM_USER/nginx.sh '$NEW_PORT' '$DOMAIN'"

if [ -n "$ACTIVE_ENV" ]; then
    echo "Stopping and removing old environment: $OLD_ENV..."
    ssh "$VM_USER@$VM_IP" "sudo docker rm -f $OLD_ENV || true"
fi

echo "========================================"
echo "4. Final Checks & HTTPS Setup"
echo "========================================"

echo "Checking if Nginx listens on port 80..."
ssh "$VM_USER@$VM_IP" "sudo ss -tulpn | grep ':80'"

echo "Checking HTTP locally on VM..."
ssh "$VM_USER@$VM_IP" "curl --connect-timeout 10 -I http://localhost"

echo "Checking HTTP via DuckDNS..."
curl --connect-timeout 10 -I "http://$DOMAIN"

echo "Requesting/Updating HTTPS certificate and reverse proxy..."
ssh "$VM_USER@$VM_IP" "sudo /home/$VM_USER/https.sh '$DOMAIN' '$EMAIL' '$NEW_PORT'"

echo "Checking HTTPS via DuckDNS..."
curl --connect-timeout 10 -I "https://$DOMAIN"

echo "Deployment completed successfully!"