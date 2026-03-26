# Deployment Plan — AWS Free Tier

This document describes how to host the Provision Calculator service on AWS for test purposes, fully within the free tier, with automated deployment triggered by GitHub Releases.

## Architecture Overview

```
GitHub Release (tag)
       │
       ▼
GitHub Actions Workflow
  ├── gradle build (produces JAR)
  └── SCP JAR to EC2 → restart systemd service
                │
                ▼
     ┌──────────────────────┐
     │  EC2 t2.micro        │
     │  Amazon Linux 2023   │
     │                      │
     │  ┌────────────────┐  │
     │  │ Spring Boot    │  │
     │  │ (port 8080)    │  │
     │  └───────┬────────┘  │
     │          │           │
     │  ┌───────▼────────┐  │
     │  │ PostgreSQL 15  │  │
     │  │ (localhost)    │  │
     │  └────────────────┘  │
     └──────────────────────┘
```

**Why this setup:**
- Single EC2 instance with PostgreSQL co-located — simplest possible setup, avoids RDS costs
- t2.micro: 750 hours/month free for 12 months (enough for 24/7 operation)
- No load balancer, no ECS, no managed DB — zero cost beyond the free tier
- GitHub Actions is free for public repos

## AWS Free Tier Components Used

| Service | Tier | Limit |
|---------|------|-------|
| EC2 t2.micro | 12-month free | 750 hrs/month, 1 vCPU, 1 GB RAM |
| EBS gp3 | 12-month free | 30 GB storage |
| Data transfer | Always free | 100 GB/month outbound |

## Step-by-Step Setup

### 1. Provision EC2 Instance (One-Time)

1. Launch an **EC2 t2.micro** instance:
   - AMI: Amazon Linux 2023
   - Instance type: t2.micro
   - Storage: 20 GB gp3 (within 30 GB free limit)
   - Security group: allow inbound **SSH (22)** and **TCP 8080** (restrict to your IP for testing)

2. Create or assign a **key pair** for SSH access.

3. Allocate an **Elastic IP** (free while associated with a running instance) so the IP doesn't change on restart.

### 2. Configure the Instance

SSH into the instance and run:

```bash
# Install Java 21
sudo dnf install -y java-21-amazon-corretto-headless

# Install and start PostgreSQL
sudo dnf install -y postgresql15-server
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

### 3. Create systemd Service (One-Time)

```bash
sudo tee /etc/systemd/system/provisioncalculator.service > /dev/null <<'EOF'
[Unit]
Description=Provision Calculator Service
After=network.target postgresql.service

[Service]
User=ec2-user
ExecStart=/usr/bin/java -jar /opt/provisioncalculator/app.jar --spring.profiles.active=aws
Restart=always
RestartSec=5
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
EOF

sudo mkdir -p /opt/provisioncalculator
sudo chown ec2-user:ec2-user /opt/provisioncalculator
sudo systemctl enable provisioncalculator
```

### 4. Add AWS Spring Profile

Create `src/main/resources/application-aws.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/provisioncalculator
    username: provision
    password: ${DB_PASSWORD:provision_secret}
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true

server:
  port: 8080
```

### 5. Store Secrets in GitHub

Go to **Settings > Secrets and variables > Actions** in your GitHub repo and add:

| Secret | Value |
|--------|-------|
| `EC2_HOST` | Your Elastic IP address |
| `EC2_SSH_KEY` | Contents of your `.pem` private key |
| `EC2_USER` | `ec2-user` |

### 6. GitHub Actions Deployment Workflow

Create `.github/workflows/deploy.yml`:

```yaml
name: Deploy on Release

on:
  release:
    types: [published]

jobs:
  deploy:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Build JAR
        run: gradle bootJar

      - name: Deploy to EC2
        env:
          SSH_KEY: ${{ secrets.EC2_SSH_KEY }}
          HOST: ${{ secrets.EC2_HOST }}
          USER: ${{ secrets.EC2_USER }}
        run: |
          # Write SSH key
          echo "$SSH_KEY" > deploy_key.pem
          chmod 600 deploy_key.pem
          SSH_OPTS="-o StrictHostKeyChecking=no -i deploy_key.pem"

          # Upload JAR
          scp $SSH_OPTS build/libs/provisioncalculator-0.0.1-SNAPSHOT.jar \
            $USER@$HOST:/opt/provisioncalculator/app.jar

          # Restart service
          ssh $SSH_OPTS $USER@$HOST "sudo systemctl restart provisioncalculator"

          # Wait and verify health
          sleep 10
          ssh $SSH_OPTS $USER@$HOST "curl -sf http://localhost:8080/api/v1/tenants/health-check/settlements || echo 'Service started (no health endpoint yet)'"

          # Cleanup
          rm -f deploy_key.pem
```

## Deployment Workflow

1. Merge your changes to the main branch
2. Go to **GitHub > Releases > Create a new release**
3. Tag with a version (e.g., `v0.1.0`), write release notes, publish
4. The GitHub Action automatically:
   - Builds the fat JAR with `gradle bootJar`
   - Copies it to the EC2 instance via SCP
   - Restarts the systemd service
5. The app is accessible at `http://<ELASTIC_IP>:8080`

## Cost Summary

| Component | Monthly Cost |
|-----------|-------------|
| EC2 t2.micro (24/7) | $0 (free tier) |
| EBS 20 GB gp3 | $0 (within 30 GB free) |
| Elastic IP (associated) | $0 |
| Data transfer (< 100 GB) | $0 |
| GitHub Actions | $0 (public repo) |
| **Total** | **$0** |

**Note:** The EC2 free tier expires after 12 months from AWS account creation. After that, a t2.micro costs ~$8.50/month. Shut down the instance when not testing to avoid charges.

## Limitations

- **Not production-grade** — single instance, no redundancy, no HTTPS, no domain
- **1 GB RAM** — t2.micro has limited memory; JVM is configured with defaults (~256 MB heap). For the 5000-node performance test, this should be sufficient
- **No HTTPS** — for test purposes only. To add HTTPS, put a free Cloudflare proxy in front or use Let's Encrypt with nginx
- **PostgreSQL on same instance** — acceptable for testing, not recommended for production

## Future Improvements (Beyond Free Tier)

If you later need a more robust setup:
- Move PostgreSQL to **RDS db.t3.micro** (still free tier for 12 months)
- Add an **Application Load Balancer** with ACM certificate for HTTPS
- Use **ECS Fargate** or **Elastic Beanstalk** for container orchestration
- Use **SSM Parameter Store** for secrets instead of GitHub Secrets
