# First Time EC2 Setup

## Install Java & Postgres

```bash
sudo apt update && sudo apt install -y openjdk-21-jdk postgresql
```

## Create Database

```bash
sudo -u postgres psql -c "CREATE DATABASE breviare;"
sudo -u postgres psql -c "CREATE USER breviare WITH PASSWORD 'yourpassword';"
sudo -u postgres psql -c "GRANT ALL PRIVILEGES ON DATABASE breviare TO breviare;"
```

## App Directory & Env File

```bash
sudo mkdir -p /opt/breviare

sudo tee /opt/breviare/.env <<EOF
DB_USER=breviare
DB_PASSWORD=yourpassword
EOF

sudo chmod 600 /opt/breviare/.env
```

## Install & Enable the Service

```bash
sudo cp breviare.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable breviare
```

## GitHub Secrets

Add these in GitHub → Settings → Secrets → Actions:

| Secret | Value |
|---|---|
| `EC2_HOST` | Your EC2 public IP or DNS |
| `EC2_USER` | `ubuntu` (for Ubuntu AMI) |
| `EC2_SSH_KEY` | Contents of your `.pem` private key |

After this, every push to `main` builds and deploys automatically.
