#!/bin/bash

set -euo pipefail


: "${GCP_PROJECT_ID:?GCP_PROJECT_ID is required}"
: "${GCP_ZONE:?GCP_ZONE is required}"
: "${VM_NAME:?VM_NAME is required}"
: "${VM_USER:?VM_USER is required}"
: "${SSH_PUBLIC_KEY:?SSH_PUBLIC_KEY is required}"

SSH_KEYS_FILE="ssh-keys.txt"

echo "========================================"
echo "VM Creation"
echo "========================================"

echo "Setting Google Cloud project to $GCP_PROJECT_ID..."
gcloud config set project "$GCP_PROJECT_ID"

echo "Preparing SSH key metadata..."
printf '%s:%s\n' "$VM_USER" "$SSH_PUBLIC_KEY" > "$SSH_KEYS_FILE"

if ! gcloud compute instances describe "$VM_NAME" --zone="$GCP_ZONE" >/dev/null 2>&1; then
  echo "VM does not exist. Creating VM: $VM_NAME"

  gcloud compute instances create "$VM_NAME" \
    --zone="$GCP_ZONE" \
    --machine-type=e2-medium \
    --image-family=debian-12 \
    --image-project=debian-cloud \
    --tags=http-server,https-server,ssh-server \
    --metadata-from-file ssh-keys="$SSH_KEYS_FILE"

  echo "VM created successfully."
else
  echo "VM already exists. Updating SSH key metadata..."

  gcloud compute instances add-metadata "$VM_NAME" \
    --zone="$GCP_ZONE" \
    --metadata-from-file ssh-keys="$SSH_KEYS_FILE"

  echo "VM metadata updated successfully."
fi

echo "VM external IP:"
gcloud compute instances describe "$VM_NAME" \
  --zone="$GCP_ZONE" \
  --format='get(networkInterfaces[0].accessConfigs[0].natIP)'