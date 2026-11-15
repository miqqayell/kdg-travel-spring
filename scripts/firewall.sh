#!/bin/bash

set -euo pipefail

NETWORK="${NETWORK:-default}"

create_or_replace_rule() {
  local RULE_NAME="$1"
  local PORT="$2"
  local TARGET_TAG="$3"
  local DESCRIPTION="$4"

  echo "Creating firewall rule: $RULE_NAME"

  gcloud compute firewall-rules delete "$RULE_NAME" --quiet || true

  gcloud compute firewall-rules create "$RULE_NAME" \
    --network="$NETWORK" \
    --direction=INGRESS \
    --allow="tcp:$PORT" \
    --source-ranges=0.0.0.0/0 \
    --target-tags="$TARGET_TAG" \
    --description="$DESCRIPTION"
}

echo "========================================"
echo "Firewall Setup"
echo "========================================"

create_or_replace_rule \
  "allow-ssh" \
  "22" \
  "ssh-server" \
  "Allow SSH access for GitLab CI/CD deployment"

create_or_replace_rule \
  "allow-http" \
  "80" \
  "http-server" \
  "Allow HTTP traffic for Let's Encrypt and redirect to HTTPS"

create_or_replace_rule \
  "allow-https" \
  "443" \
  "https-server" \
  "Allow HTTPS traffic for public application access"

echo "Firewall setup completed successfully"