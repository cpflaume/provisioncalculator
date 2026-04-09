#!/bin/bash
set -euo pipefail

echo "=== Provision Calculator - OCI VM Setup ==="
echo ""

# --- Java 21 ---
echo "[1/8] Installing Java 21..."
sudo dnf install -y java-21-openjdk-headless
java -version
echo ""

# --- PostgreSQL ---
echo "[2/8] Installing PostgreSQL..."
sudo dnf install -y postgresql-server
if [ -z "$(sudo ls -A /var/lib/pgsql/data 2>/dev/null)" ]; then
    sudo postgresql-setup --initdb
fi
sudo systemctl enable postgresql
sudo systemctl start postgresql

# Create database and user (idempotent: skip if already exists)
sudo -u postgres psql -tc "SELECT 1 FROM pg_roles WHERE rolname='provision'" | grep -q 1 \
    || sudo -u postgres psql -c "CREATE USER provision WITH PASSWORD 'provision_secret';"
sudo -u postgres psql -tc "SELECT 1 FROM pg_database WHERE datname='provisioncalculator'" | grep -q 1 \
    || sudo -u postgres psql -c "CREATE DATABASE provisioncalculator OWNER provision;"

# Switch from ident to md5 authentication for local connections
sudo sed -i 's/ident$/md5/' /var/lib/pgsql/data/pg_hba.conf
sudo systemctl restart postgresql
echo "PostgreSQL ready."
echo ""

# --- Firewall ---
echo "[3/8] Configuring firewall (HTTP/HTTPS)..."
sudo firewall-cmd --permanent --remove-port=8080/tcp 2>/dev/null || true
sudo firewall-cmd --permanent --add-port=80/tcp
sudo firewall-cmd --permanent --add-port=443/tcp
sudo firewall-cmd --reload
echo ""

# --- App directory ---
echo "[4/8] Creating application directory..."
sudo mkdir -p /opt/provisioncalculator
sudo chown opc:opc /opt/provisioncalculator
echo ""

# --- systemd service ---
echo "[5/8] Creating systemd service..."
sudo tee /etc/systemd/system/provisioncalculator.service > /dev/null <<'SERVICE'
[Unit]
Description=Provision Calculator Service
After=network.target postgresql.service

[Service]
User=opc
ExecStart=/usr/bin/java -jar /opt/provisioncalculator/app.jar --spring.profiles.active=oci
Restart=always
RestartSec=5
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
SERVICE

sudo systemctl daemon-reload
sudo systemctl enable provisioncalculator
echo ""

# --- Caddy ---
echo "[6/8] Installing Caddy..."
sudo dnf install -y 'dnf-command(copr)'
sudo dnf copr enable -y @caddy/caddy
sudo dnf install -y caddy
sudo setcap 'cap_net_bind_service=+ep' /usr/bin/caddy
echo ""

# --- Frontend directory ---
echo "[7/9] Creating frontend directory..."
sudo mkdir -p /var/www/provisioncalculator-fe
sudo chown opc:opc /var/www/provisioncalculator-fe
echo ""

# --- SELinux context for web files ---
echo "[8/9] Configuring SELinux for web directory..."
if command -v getenforce &>/dev/null && [ "$(getenforce)" != "Disabled" ]; then
    sudo semanage fcontext -a -t httpd_sys_content_t "/var/www/provisioncalculator-fe(/.*)?" 2>/dev/null || true
    sudo restorecon -Rv /var/www/provisioncalculator-fe
    echo "SELinux context set to httpd_sys_content_t"
else
    echo "SELinux not active, skipping."
fi
echo ""

# --- Caddyfile ---
# NOTE: The canonical Caddyfile is maintained in the provisioncalculator-fe repo
# and deployed automatically on every frontend release. This copy is for initial
# VM setup only.
echo "[9/9] Configuring Caddy..."
sudo tee /etc/caddy/Caddyfile > /dev/null <<'CADDYFILE'
provisioncalculator.copf-demo.de {
    encode gzip

    handle /api/* {
        reverse_proxy localhost:8080
    }

    handle /swagger-ui* {
        reverse_proxy localhost:8080
    }

    handle /v3/api-docs* {
        reverse_proxy localhost:8080
    }

    handle {
        root * /var/www/provisioncalculator-fe
        try_files {path} /index.html
        file_server
    }
}
CADDYFILE

sudo systemctl enable caddy
sudo systemctl restart caddy
echo ""

echo "=== Setup complete! ==="
echo ""
echo "Next steps:"
echo "  1. Ensure DNS A record for provisioncalculator.copf-demo.de points to this VM's public IP"
echo "  2. Add OCI Security Rule: open ports 80 and 443 (TCP) for 0.0.0.0/0"
echo "  3. Add GitHub Secrets (ORACLE_VM_SSH_KEY, OCI_HOST) to both repos"
echo "  4. Create a GitHub Release (backend) to trigger first BE deployment"
echo "  5. Push to main (frontend) to trigger first FE deployment"
echo ""
echo "Verify with:"
echo "  java -version"
echo "  sudo -u postgres psql -d provisioncalculator -c 'SELECT 1;'"
echo "  sudo systemctl status provisioncalculator"
echo "  sudo systemctl status caddy"
echo "  curl -s https://provisioncalculator.copf-demo.de/"
