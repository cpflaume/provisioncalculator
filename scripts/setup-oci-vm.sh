#!/bin/bash
set -euo pipefail

# --- Configurable inputs (all optional) ---
# BACKUP_DB_PASSWORD    : password for the read/write backup DB user.
#                         If empty, a random one is generated and printed once.
# REMOTE_DB_ALLOWED_IP  : IPv4/CIDR that may reach PostgreSQL directly over the
#                         network (e.g. 203.0.113.10 or 203.0.113.0/24). If empty,
#                         PostgreSQL stays bound to localhost only and remote access
#                         is expected via SSH tunnel.
BACKUP_DB_PASSWORD="${BACKUP_DB_PASSWORD:-}"
REMOTE_DB_ALLOWED_IP="${REMOTE_DB_ALLOWED_IP:-}"

PG_HBA="/var/lib/pgsql/data/pg_hba.conf"
PG_CONF="/var/lib/pgsql/data/postgresql.conf"

echo "=== Provision Calculator - OCI VM Setup ==="
echo ""

# --- Java 21 ---
echo "[1/10] Installing Java 21..."
sudo dnf install -y java-21-openjdk-headless
java -version
echo ""

# --- PostgreSQL ---
echo "[2/10] Installing PostgreSQL..."
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

# --- Backup user (read/write) ---
# Used for pg_dump/pg_restore and ad-hoc access from another host.
if [ -z "$BACKUP_DB_PASSWORD" ]; then
    BACKUP_DB_PASSWORD="$(openssl rand -base64 24 | tr -d '/+=')"
    GENERATED_BACKUP_PW=1
fi
sudo -u postgres psql -tc "SELECT 1 FROM pg_roles WHERE rolname='backup'" | grep -q 1 \
    && sudo -u postgres psql -c "ALTER USER backup WITH PASSWORD '${BACKUP_DB_PASSWORD}';" \
    || sudo -u postgres psql -c "CREATE USER backup WITH PASSWORD '${BACKUP_DB_PASSWORD}';"

# Grant read/write on the app database. Tables are owned by 'provision' and created
# later by Flyway, so ALTER DEFAULT PRIVILEGES FOR ROLE provision covers future tables too.
sudo -u postgres psql -d provisioncalculator <<SQL
GRANT CONNECT ON DATABASE provisioncalculator TO backup;
GRANT USAGE ON SCHEMA public TO backup;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO backup;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO backup;
ALTER DEFAULT PRIVILEGES FOR ROLE provision IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO backup;
ALTER DEFAULT PRIVILEGES FOR ROLE provision IN SCHEMA public GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO backup;
SQL

# Switch from ident to md5 authentication for local connections
sudo sed -i 's/ident$/md5/' "$PG_HBA"

# --- Optional: allow direct network access from a trusted host ---
# When REMOTE_DB_ALLOWED_IP is set, PostgreSQL listens on all interfaces and accepts
# md5 connections to the app database from that IP/CIDR only. Otherwise it stays on
# localhost and remote access should go through an SSH tunnel (no config needed).
if [ -n "$REMOTE_DB_ALLOWED_IP" ]; then
    echo "Enabling direct network access from ${REMOTE_DB_ALLOWED_IP}..."
    if ! sudo grep -qE "^\s*listen_addresses\s*=\s*'\*'" "$PG_CONF"; then
        echo "listen_addresses = '*'" | sudo tee -a "$PG_CONF" > /dev/null
    fi
    HBA_LINE="host    provisioncalculator    all    ${REMOTE_DB_ALLOWED_IP}    md5"
    sudo grep -qF "$HBA_LINE" "$PG_HBA" || echo "$HBA_LINE" | sudo tee -a "$PG_HBA" > /dev/null
    sudo firewall-cmd --permanent --add-rich-rule="rule family=ipv4 source address=${REMOTE_DB_ALLOWED_IP} port port=5432 protocol=tcp accept"
    sudo firewall-cmd --reload
    echo "NOTE: also open TCP 5432 from ${REMOTE_DB_ALLOWED_IP} in the OCI VCN Security List."
else
    echo "PostgreSQL stays on localhost. For remote access use an SSH tunnel, e.g.:"
    echo "  ssh -N -L 5432:localhost:5432 opc@<VM_PUBLIC_IP>"
fi

sudo systemctl restart postgresql
echo "PostgreSQL ready."
if [ "${GENERATED_BACKUP_PW:-0}" = "1" ]; then
    echo ""
    echo "  >>> Generated backup user password (store it now, not shown again):"
    echo "  >>> backup / ${BACKUP_DB_PASSWORD}"
fi
echo ""

# --- Firewall ---
echo "[3/10] Configuring firewall (HTTP/HTTPS)..."
sudo firewall-cmd --permanent --remove-port=8080/tcp 2>/dev/null || true
sudo firewall-cmd --permanent --add-port=80/tcp
sudo firewall-cmd --permanent --add-port=443/tcp
sudo firewall-cmd --reload
echo ""

# --- App directory ---
echo "[4/10] Creating application directory..."
sudo mkdir -p /opt/provisioncalculator
sudo chown opc:opc /opt/provisioncalculator
echo ""

# --- Environment file (secrets written by deploy/rotate-secrets workflows) ---
echo "[5/10] Creating placeholder environment file..."
if [ ! -f /etc/provisioncalculator.env ]; then
    sudo tee /etc/provisioncalculator.env > /dev/null <<'ENV'
# Populated by the deploy or rotate-secrets workflow
JWT_SECRET=
DB_PASSWORD=
ENV
    sudo chmod 600 /etc/provisioncalculator.env
fi
echo ""

# --- systemd service ---
echo "[6/10] Creating systemd service..."
sudo tee /etc/systemd/system/provisioncalculator.service > /dev/null <<'SERVICE'
[Unit]
Description=Provision Calculator Service
After=network.target postgresql.service

[Service]
User=opc
EnvironmentFile=/etc/provisioncalculator.env
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
echo "[7/10] Installing Caddy..."
sudo dnf install -y 'dnf-command(copr)'
sudo dnf copr enable -y @caddy/caddy
sudo dnf install -y caddy
sudo setcap 'cap_net_bind_service=+ep' /usr/bin/caddy
echo ""

# --- Frontend directory ---
echo "[8/10] Creating frontend directory..."
sudo mkdir -p /var/www/provisioncalculator-fe
sudo chown opc:opc /var/www/provisioncalculator-fe
echo ""

# --- SELinux context for web files ---
echo "[9/10] Configuring SELinux for web directory..."
if command -v getenforce &>/dev/null && [ "$(getenforce)" != "Disabled" ]; then
    sudo semanage fcontext -a -t httpd_sys_content_t "/var/www/provisioncalculator-fe(/.*)" 2>/dev/null || true
    sudo restorecon -Rv /var/www/provisioncalculator-fe
    echo "SELinux context set to httpd_sys_content_t"
else
    echo "SELinux not active, skipping."
fi
echo ""

# --- Caddyfile (placeholder; overwritten on every deploy) ---
echo "[10/10] Configuring Caddy..."
sudo tee /etc/caddy/Caddyfile > /dev/null <<'CADDYFILE'
provisioncalculator.copf-demo.de {
    respond "Coming soon" 200
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
echo "  3. Add GitHub Secrets (ORACLE_VM_SSH_KEY, OCI_HOST, JWT_SECRET, DB_PASSWORD) to the backend repo"
echo "  4. Create a GitHub Release (backend) to trigger first BE deployment"
echo "  5. Push to main (frontend) to trigger first FE deployment"
echo ""
echo "Verify with:"
echo "  java -version"
echo "  sudo -u postgres psql -d provisioncalculator -c 'SELECT 1;'"
echo "  sudo -u postgres psql -c '\\du backup'"
echo "  sudo systemctl status provisioncalculator"
echo "  sudo systemctl status caddy"
echo "  curl -s https://provisioncalculator.copf-demo.de/"
