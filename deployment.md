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
- [x] `.github/workflows/create-vm.yml` — Auto-retry VM creation on "Out of capacity" (in repo)
- [x] `.github/workflows/setup-vm.yml` — One-click VM provisioning via SSH (in repo)
- [x] `scripts/setup-oci-vm.sh` — Setup script for Java, PostgreSQL, systemd (in repo)
- [ ] Add OCI CLI secrets to GitHub (see table below)
- [ ] Run **Create OCI VM** workflow (retries automatically until ARM is available)
- [ ] Run **Setup OCI VM** workflow (installs Java, PostgreSQL, systemd service)
- [ ] Add `OCI_HOST` secret with the VM's public IP
- [ ] Create first GitHub Release to trigger deployment

## GitHub Secrets Required

Go to **Settings > Secrets and variables > Actions** and add:

| Secret | Where to find it | Purpose |
|--------|-----------------|---------|
| `OCI_CLI_TENANCY` | OCI Console > Profile > Tenancy OCID | OCI API auth |
| `OCI_CLI_USER` | OCI Console > Profile > User OCID | OCI API auth |
| `OCI_CLI_FINGERPRINT` | OCI Console > Profile > API Keys > Fingerprint | OCI API auth |
| `OCI_CLI_KEY_CONTENT` | The private key (PEM) you downloaded when creating the API key | OCI API auth |
| `OCI_CLI_REGION` | e.g. `eu-frankfurt-1` | OCI region |
| `OCI_COMPARTMENT_ID` | OCI Console > Identity > Compartments > root > OCID | Where to create resources |
| `OCI_AVAILABILITY_DOMAIN` | OCI Console > Compute > Availability Domains (e.g. `Xyzz:EU-FRANKFURT-1-AD-1`) | VM placement |
| `OCI_SSH_PUBLIC_KEY` | Your `~/.ssh/id_rsa.pub` content | SSH into VM |
| `ORACLE_VM_SSH_KEY` | Your `~/.ssh/id_rsa` private key content | Used by deploy + setup workflows |

**To create an OCI API Key:** Profile icon (top right) > My profile > API keys > Add API key > Generate API key pair > Download both keys.

## Automated Workflows

| Workflow | Trigger | What it does |
|----------|---------|-------------|
| **Create OCI VM** | Manual + every 2h (cron) | Creates VM via OCI Resource Manager Stack. Skips if VM already running. Retries automatically on "Out of capacity". |
| **Setup OCI VM** | Manual | SSHs into VM, installs Java 21, PostgreSQL, creates DB, systemd service |
| **Deploy on Release** | GitHub Release published | Builds JAR, SCPs to VM, restarts service |

## Step-by-Step Setup

### 1. Create the VM Instance (Automated)

The VM is created automatically via Terraform (OCI Resource Manager Stack):

1. Add all GitHub Secrets from the table above
2. Go to **Actions > "Create OCI VM" > Run workflow**
3. If ARM capacity is unavailable, the workflow retries automatically every 2 hours
4. Once successful, the job summary shows the **Public IP**
5. Add the IP as GitHub Secret `OCI_HOST`

The VM is created via an existing OCI Resource Manager Stack. The workflow checks the last job status and retries if it failed.

**Manual alternative:** You can also create the VM in the OCI Console (Compute > Instances > Create Instance) with shape `VM.Standard.A1.Flex`, 1 OCPU, 6 GB RAM, Oracle Linux 9.

### 2. Provision the VM (Automated)

Once the VM is running and `OCI_HOST` secret is set:

1. Go to **Actions > "Setup OCI VM" > Run workflow**
2. This runs `scripts/setup-oci-vm.sh` on the VM via SSH, which installs:
   - Java 21
   - PostgreSQL + database `provisioncalculator`
   - Firewall rule for port 8080
   - systemd service `provisioncalculator`

**Manual alternative:** SSH into the VM and run the script yourself:

```bash
ssh -i your_key opc@<PUBLIC_IP>
# then copy/paste scripts/setup-oci-vm.sh content
```

### 3. First Deployment

1. Create a GitHub Release (tag e.g. `v0.1.0`)
2. The **Deploy on Release** workflow automatically builds the JAR and deploys it
3. The app is accessible at `http://<PUBLIC_IP>:8080`
4. Swagger UI is at `http://<PUBLIC_IP>:8080/swagger-ui.html`

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
