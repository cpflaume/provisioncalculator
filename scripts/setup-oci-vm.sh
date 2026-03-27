#!/bin/bash
set -euo pipefail

echo "=== Provision Calculator - OCI VM Setup ==="
echo ""

# --- Java 21 ---
echo "[1/5] Installing Java 21..."
sudo dnf install -y java-21-openjdk-headless
java -version
echo ""

# --- PostgreSQL ---
echo "[2/5] Installing PostgreSQL..."
sudo dnf install -y postgresql-server
sudo postgresql-setup --initdb
sudo systemctl enable postgresql
sudo systemctl start postgresql

# Create database and user
sudo -u postgres psql -c "CREATE USER provision WITH PASSWORD 'provision_secret';"
sudo -u postgres psql -c "CREATE DATABASE provisioncalculator OWNER provision;"

# Switch from ident to md5 authentication for local connections
sudo sed -i 's/ident$/md5/' /var/lib/pgsql/data/pg_hba.conf
sudo systemctl restart postgresql
echo "PostgreSQL ready."
echo ""

# --- Firewall ---
echo "[3/5] Opening port 8080 in firewall..."
sudo firewall-cmd --permanent --add-port=8080/tcp
sudo firewall-cmd --reload
echo ""

# --- App directory ---
echo "[4/5] Creating application directory..."
sudo mkdir -p /opt/provisioncalculator
sudo chown opc:opc /opt/provisioncalculator
echo ""

# --- systemd service ---
echo "[5/5] Creating systemd service..."
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

echo "=== Setup complete! ==="
echo ""
echo "Next steps:"
echo "  1. Add OCI Security Rule: open port 8080 (TCP) for 0.0.0.0/0"
echo "  2. Add GitHub Secret OCI_HOST with this VM's public IP"
echo "  3. Create a GitHub Release to trigger deployment"
echo ""
echo "Verify with:"
echo "  java -version"
echo "  sudo -u postgres psql -d provisioncalculator -c 'SELECT 1;'"
echo "  sudo systemctl status provisioncalculator"
