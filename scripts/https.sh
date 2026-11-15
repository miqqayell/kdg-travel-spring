#!/bin/bash
set -euo pipefail

DOMAIN="${1:?DOMAIN is required}"
EMAIL="${2:?EMAIL is required}"
APP_PORT="${3:?APP_PORT is required}"

NGINX_AVAILABLE="/etc/nginx/sites-available/int4-app"
NGINX_ENABLED="/etc/nginx/sites-enabled/int4-app"

echo "Running Certbot with Nginx plugin..."

sudo ln -sf "$NGINX_AVAILABLE" "$NGINX_ENABLED"
sudo rm -f /etc/nginx/sites-enabled/default

sudo nginx -t
sudo systemctl reload nginx

sudo certbot --nginx \
  -d "$DOMAIN" \
  --non-interactive \
  --agree-tos \
  --email "$EMAIL" \
  --redirect \
  --reinstall

echo "Writing final HTTPS reverse proxy configuration..."

sudo tee "$NGINX_AVAILABLE" > /dev/null <<EOF
server {
    listen 80;
    listen [::]:80;
    server_name $DOMAIN;

    return 301 https://\$host\$request_uri;
}

server {
    listen 443 ssl;
    listen [::]:443 ssl;
    server_name $DOMAIN;

    ssl_certificate /etc/letsencrypt/live/$DOMAIN/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/$DOMAIN/privkey.pem;
    include /etc/letsencrypt/options-ssl-nginx.conf;
    ssl_dhparam /etc/letsencrypt/ssl-dhparams.pem;

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

sudo ln -sf "$NGINX_AVAILABLE" "$NGINX_ENABLED"
sudo rm -f /etc/nginx/sites-enabled/default

sudo nginx -t
sudo systemctl reload nginx

echo "HTTPS reverse proxy successfully configured for $DOMAIN on port $APP_PORT"