#!/bin/bash
set -euo pipefail

DOMAIN="${2:?DOMAIN is required}"
APP_PORT="${1:-8080}"

echo "Configuring Nginx reverse proxy for 127.0.0.1:$APP_PORT"

sudo tee /etc/nginx/sites-available/int4-app > /dev/null <<EOF
server {
    listen 80;
    server_name $DOMAIN;

    location / {
        proxy_pass http://127.0.0.1:$APP_PORT;
        proxy_http_version 1.1;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }
}
EOF

sudo ln -sf /etc/nginx/sites-available/int4-app /etc/nginx/sites-enabled/int4-app
sudo rm -f /etc/nginx/sites-enabled/default

sudo nginx -t
sudo systemctl reload nginx