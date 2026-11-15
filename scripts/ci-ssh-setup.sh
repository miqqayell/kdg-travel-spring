#!/bin/bash

set -euo pipefail

: "${SSH_PRIVATE_KEY:?SSH_PRIVATE_KEY is required}"
: "${VM_IP:?VM_IP is required}"

SSH_DIR="$HOME/.ssh"
SSH_KEY_FILE="$SSH_DIR/id_ed25519"
MAX_ATTEMPTS=30
WAIT_SECONDS=10

echo "========================================"
echo "CI SSH Setup"
echo "========================================"

echo "Installing SSH client tools..."
apt-get update -y
apt-get install -y openssh-client netcat-openbsd curl

echo "Creating SSH directory..."
mkdir -p "$SSH_DIR"
chmod 700 "$SSH_DIR"

echo "Writing SSH private key..."
printf '%s\n' "$SSH_PRIVATE_KEY" > "$SSH_KEY_FILE"
chmod 600 "$SSH_KEY_FILE"

echo "Validating SSH private key..."
ssh-keygen -y -f "$SSH_KEY_FILE" > /dev/null

echo "Waiting for SSH to become available on $VM_IP:22..."

for i in $(seq 1 "$MAX_ATTEMPTS"); do
  if nc -vz -w 5 "$VM_IP" 22; then
    echo "SSH is available."
    break
  fi

  echo "SSH attempt $i/$MAX_ATTEMPTS failed. Waiting $WAIT_SECONDS seconds..."
  sleep "$WAIT_SECONDS"

  if [ "$i" -eq "$MAX_ATTEMPTS" ]; then
    echo "ERROR: SSH did not become available on $VM_IP:22"
    exit 1
  fi
done

echo "Adding VM host key to known_hosts..."
ssh-keyscan -H "$VM_IP" >> "$SSH_DIR/known_hosts"

echo "CI SSH setup completed successfully"