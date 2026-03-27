# Deployment Plan — Oracle Cloud Always Free

This document describes how to host the Provision Calculator service on Oracle Cloud for test purposes, **permanently free** (no expiration), with automated deployment triggered by GitHub Releases.

## Architecture Overview

```
GitHub Release (tag)
       │
       ▼
GitHub Actions Workflow
  ├── gradle build (produces JAR)
  └── SCP JAR to OCI VM → restart systemd service
                │
                ▼
     ┌──────────────────────────┐
     │  OCI VM.Standard.A1.Flex │
     │  Oracle Linux 9 (ARM)   │
     │  1 OCPU / 6 GB RAM      │
     │                          │
     │  ┌────────────────┐      │
     │  │ Spring Boot    │      │
     │  │ (port 8080)    │      │
     │  └───────┬────────┘      │
     │          │               │
     │  ┌───────▼────────┐      │
     │  │ PostgreSQL 15  │      │
     │  │ (localhost)    │      │
     │  └────────────────┘      │
     └──────────────────────────┘
```

**Why Oracle Cloud Always Free:**
- **Permanently free** — no 12-month expiration, no surprise bills
- ARM VM with 1 OCPU + 6 GB RAM — 6x more memory than AWS t2.micro
- 50 GB boot volume included
- GitHub Actions is free for public repos

## Oracle Cloud Always Free Resources Used

| Resource | Always Free Limit | We Use |
|----------|------------------|--------|
| ARM VM (Ampere A1) | 4 OCPUs + 24 GB RAM total (across up to 4 VMs) | 1 OCPU + 6 GB RAM |
| Boot Volume | 200 GB total | 50 GB |
| Outbound Traffic | 10 TB/month | minimal |
| Object Storage | 20 GB | not needed |

## Progress Checklist

- [x] Create Oracle Cloud Always Free account
- [x] `application-oci.yml` — Spring profile for OCI (in repo)
- [x] `.github/workflows/deploy.yml` — Release-triggered deployment (in repo)
- [ ] Create ARM VM instance (Step 1 below)
- [ ] Configure security rules (Step 2)
- [ ] Install Java 21 + PostgreSQL on VM (Step 3)
- [ ] Create systemd service (Step 4)
- [ ] Add GitHub secrets `OCI_HOST` + `OCI_SSH_KEY` (Step 5)
- [ ] Create first GitHub Release to trigger deployment

## Step-by-Step Setup

### 1. Create the VM Instance (One-Time)

1. Go to **Compute > Instances > Create Instance**
2. Configure:
   - **Name:** `provisioncalculator`
   - **Image:** Oracle Linux 9
   - **Shape:** VM.Standard.A1.Flex (Ampere ARM)
     - OCPUs: **1**
     - Memory: **6 GB**
   - **Boot volume:** 50 GB (within Always Free)
   - **Networking:** Create new VCN + public subnet, assign public IP
   - **SSH key:** Upload or paste your public key
3. Click **Create**

### 3. Configure Security Rules

In **Networking > Virtual Cloud Networks > your VCN > Security Lists > Default**:

Add an **Ingress Rule**:
- Source CIDR: `0.0.0.0/0` (or restrict to your IP)
- Destination Port: **8080**
- Protocol: TCP

SSH (port 22) is open by default.

### 4. Configure the Instance

SSH into the instance:

```bash
ssh -i your_key opc@<PUBLIC_IP>
```

Install Java and PostgreSQL:

```bash
# Install Java 21 (ARM build)
sudo dnf install -y java-21-openjdk-headless

# Install PostgreSQL
sudo dnf install -y postgresql-server
sudo postgresql-setup --initdb
sudo systemctl enable postgresql
sudo systemctl start postgresql

# Create database and user
sudo -u postgres psql -c "CREATE USER provision WITH PASSWORD 'provision_secret';"
sudo -u postgres psql -c "CREATE DATABASE provisioncalculator OWNER provision;"

# Allow password auth for local connections
sudo sed -i 's/ident$/md5/' /var/lib/pgsql/data/pg_hba.conf
sudo systemctl restart postgresql
```

Open firewall on the OS level:

```bash
sudo firewall-cmd --permanent --add-port=8080/tcp
sudo firewall-cmd --reload
```

### 5. Create systemd Service (One-Time)

```bash
sudo tee /etc/systemd/system/provisioncalculator.service > /dev/null <<'EOF'
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
EOF

sudo mkdir -p /opt/provisioncalculator
sudo chown opc:opc /opt/provisioncalculator
sudo systemctl daemon-reload
sudo systemctl enable provisioncalculator
```

### 5. Store Secrets in GitHub

Go to **Settings > Secrets and variables > Actions** in your GitHub repo and add:

| Secret | Value |
|--------|-------|
| `OCI_HOST` | Your VM's public IP address |
| `OCI_SSH_KEY` | Contents of your private SSH key |

### Files Already in the Repository

These files are ready to use — no manual creation needed:

| File | Purpose |
|------|---------|
| `src/main/resources/application-oci.yml` | Spring profile for OCI (PostgreSQL on localhost, Flyway enabled) |
| `.github/workflows/deploy.yml` | GitHub Actions workflow triggered on Release publish |

## Deployment Workflow

1. Merge your changes to the main branch
2. Go to **GitHub > Releases > Create a new release**
3. Tag with a version (e.g., `v0.1.0`), write release notes, publish
4. The GitHub Action automatically:
   - Builds the fat JAR with `gradle bootJar`
   - Copies it to the OCI VM via SCP
   - Restarts the systemd service
5. The app is accessible at `http://<PUBLIC_IP>:8080`

## Cost Summary

| Component | Monthly Cost | Expiration |
|-----------|-------------|------------|
| VM.Standard.A1.Flex (1 OCPU, 6 GB) | $0 | **Never** |
| Boot Volume 50 GB | $0 | **Never** |
| Outbound Traffic | $0 | **Never** |
| Public IP | $0 | **Never** |
| GitHub Actions | $0 (public repo) | **Never** |
| **Total** | **$0** | **Permanent** |

## Limitations

- **Not production-grade** — single instance, no redundancy, no HTTPS, no domain
- **ARM architecture** — the Spring Boot fat JAR runs on any JVM, so ARM is no issue. JDK 21 has full ARM support
- **No HTTPS** — for test purposes only. To add HTTPS later, use a free Cloudflare proxy or Let's Encrypt with nginx
- **PostgreSQL on same instance** — acceptable for testing, not recommended for production
- **OCI ARM capacity** — ARM instances are in high demand. If creation fails with "out of capacity", retry later or try a different availability domain

## Useful Commands

```bash
# Check service status
sudo systemctl status provisioncalculator

# View logs
sudo journalctl -u provisioncalculator -f

# Restart service
sudo systemctl restart provisioncalculator

# Check PostgreSQL
sudo systemctl status postgresql
sudo -u postgres psql -d provisioncalculator -c "SELECT count(*) FROM settlement;"
```
