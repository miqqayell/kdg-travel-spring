#!/bin/bash

set -euo pipefail

echo "========================================"
echo "VM Setup"
echo "========================================"

echo "Updating package lists..."
sudo apt-get update -y

echo "Installing required packages..."
sudo apt-get install -y \
  docker.io \
  nginx \
  certbot \
  python3-certbot-nginx \
  dnsutils \
  curl

echo "Enabling and starting Docker..."
sudo systemctl enable docker
sudo systemctl start docker

echo "Enabling and starting Nginx..."
sudo systemctl enable nginx
sudo systemctl start nginx

echo "Checking installed versions..."
docker --version
nginx -v
certbot --version

echo "VM setup completed successfully"