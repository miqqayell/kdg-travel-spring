#!/bin/bash

set -euo pipefail


: "${DB_INSTANCE:?DB_INSTANCE is required}"
: "${DB_NAME:?DB_NAME is required}"
: "${DB_USER:?DB_USER is required}"
: "${DB_PASSWORD:?DB_PASSWORD is required}"
: "${VM_IP:?VM_IP is required}"

REGION="${GCP_REGION:-europe-west1}"

echo "========================================"
echo "Cloud SQL Setup"
echo "========================================"

echo "Region: $REGION"
echo "Cloud SQL instance: $DB_INSTANCE"
echo "Database name: $DB_NAME"
echo "Database user: $DB_USER"

echo "Checking Cloud SQL instance..."

if ! gcloud sql instances describe "$DB_INSTANCE" >/dev/null 2>&1; then
  echo "Cloud SQL instance does not exist. Creating instance..."

  gcloud sql instances create "$DB_INSTANCE" \
    --database-version=POSTGRES_15 \
    --tier=db-f1-micro \
    --region="$REGION" \
    --storage-size=10GB \
    --authorized-networks="$VM_IP/32"

  echo "Cloud SQL instance created successfully."
else
  echo "Cloud SQL instance already exists."
fi

echo "Creating database if it does not exist..."

if ! gcloud sql databases describe "$DB_NAME" --instance="$DB_INSTANCE" >/dev/null 2>&1; then
  gcloud sql databases create "$DB_NAME" \
    --instance="$DB_INSTANCE"

  echo "Database created successfully."
else
  echo "Database already exists."
fi

echo "Creating database user if it does not exist..."

if ! gcloud sql users list --instance="$DB_INSTANCE" --format="value(name)" | grep -qx "$DB_USER"; then
  gcloud sql users create "$DB_USER" \
    --instance="$DB_INSTANCE" \
    --password="$DB_PASSWORD"

  echo "Database user created successfully."
else
  echo "Database user already exists."
fi

echo "Restricting database access to the VM IP only: $VM_IP/32"

gcloud sql instances patch "$DB_INSTANCE" \
  --authorized-networks="$VM_IP/32" \
  --quiet

echo "Cloud SQL setup completed successfully"