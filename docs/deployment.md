# Deployment: Google Cloud with GitLab CI/CD

## Overview

The project was deployed as a Spring Boot application to Google Cloud using a GitLab CI/CD pipeline.

The application runs on a Google Compute Engine VM. The database runs on Google Cloud SQL PostgreSQL. Public traffic is handled by an Nginx reverse proxy on the VM.

The deployment uses a blue-green strategy with two local application containers:

```text
myapp-blue  -> 127.0.0.1:8080
myapp-green -> 127.0.0.1:8081
```

Only one container receives traffic at a time. The new version is started on the inactive port first, then Nginx switches traffic to it after a successful health check.

No Google Cloud Load Balancer is used. Nginx acts as the own reverse proxy and traffic switch.

## Architecture

```text
User
  |
  v
DuckDNS domain
https://<team-subdomain>.duckdns.org
  |
  v
Google Cloud Firewall
Open ports: 22, 80, 443
  |
  v
Google Compute Engine VM - Debian 12
  |
  v
Nginx Reverse Proxy
  |
  +--> myapp-blue  on 127.0.0.1:8080
  |
  +--> myapp-green on 127.0.0.1:8081
  |
  v
Google Cloud SQL PostgreSQL
Access allowed only from the VM IP
```

## GitLab CI/CD Pipeline

The pipeline is defined in:

```text
.gitlab-ci.yml
```

It contains four stages:

```yaml
stages:
  - build
  - test
  - deploy
  - destroy
```

## Build Stage

The build stage uses:

```yaml
image: gradle:8-jdk21
```

It builds the Spring Boot JAR file:

```bash
gradle clean bootJar -x test
```

The generated JAR is stored as an artifact:

```text
build/libs/*.jar
```

This artifact is used later by the deploy stage.

## Test Stage

The test stage runs JUnit tests using Gradle:

```bash
gradle test
```

A temporary PostgreSQL service is started for integration tests:

```yaml
services:
  - name: postgres:15
    alias: postgres
```

The Spring Boot test environment connects to this database using test datasource variables. Test results are available in GitLab as JUnit reports.

## Deploy Stage

The deploy stage uses:

```yaml
image: google/cloud-sdk:slim
```

It performs the following tasks:

```text
Authenticates to Google Cloud
Creates or updates firewall rules
Creates or updates the VM
Creates or updates the Cloud SQL database
Prepares SSH access to the VM
Uploads the application JAR
Runs the blue-green deployment
Configures Nginx and HTTPS
```

Before deployment, the pipeline checks that all required GitLab CI/CD variables are available. This prevents deployment from starting with missing configuration.

## Destroy Stage

The destroy stage is manual:

```yaml
when: manual
```

It should be executed at the end of the working session to avoid unnecessary Google Cloud costs.

It removes:

```text
Google Compute Engine VM
Google Cloud SQL instance
Firewall rules
```

## GitLab CI/CD Variables

Sensitive data is stored in GitLab CI/CD variables, not in the repository.

Required variables:

| Variable             | Purpose                             |
| -------------------- | ----------------------------------- |
| `GCLOUD_SERVICE_KEY` | Google Cloud service account key    |
| `GCP_PROJECT_ID`     | Google Cloud project ID             |
| `GCP_REGION`         | Cloud SQL region                    |
| `GCP_ZONE`           | VM zone                             |
| `VM_NAME`            | VM name                             |
| `VM_USER`            | SSH user                            |
| `SSH_PRIVATE_KEY`    | Private SSH key for deployment      |
| `SSH_PUBLIC_KEY`     | Public SSH key added to VM metadata |
| `DB_INSTANCE`        | Cloud SQL instance name             |
| `DB_NAME`            | PostgreSQL database name            |
| `DB_USER`            | PostgreSQL database username        |
| `DB_PASSWORD`        | PostgreSQL database password        |
| `DOMAIN`             | Public domain                       |
| `DUCKDNS_SUBDOMAIN`  | DuckDNS subdomain                   |
| `DUCKDNS_TOKEN`      | DuckDNS token                       |
| `EMAIL`              | Email for Let's Encrypt certificate |

## Infrastructure Scripts

All infrastructure scripts are stored in:

```text
scripts/
```

They are version controlled, so the project follows the Infrastructure as Code principle.

## Script Overview

### `scripts/firewall.sh`

Creates the required Google Cloud firewall rules.

Opened ports:

```text
22  - SSH for GitLab CI/CD
80  - HTTP for Let's Encrypt and redirect
443 - HTTPS for public access
```

Application ports `8080` and `8081` are not opened publicly.

### `scripts/create-vm.sh`

Creates the Google Compute Engine VM if it does not exist.

Main configuration:

```text
Machine type: e2-medium
Image: Debian 12
Tags: ssh-server, http-server, https-server
```

If the VM already exists, the script updates SSH metadata.

### `scripts/create-db.sh`

Creates or verifies the Cloud SQL PostgreSQL setup:

```text
Cloud SQL instance
Application database
Database user
Authorized network
```

Database access is restricted to:

```text
VM_IP/32
```

So only the application VM can access the database.

### `scripts/ci-ssh-setup.sh`

Prepares SSH access from the GitLab runner to the VM.

It:

```text
Installs SSH tools
Writes the SSH private key
Validates the key
Waits for SSH on the VM
Adds the VM to known_hosts
```

### `scripts/setup-vm.sh`

Installs required software on the VM:

```text
Docker
Nginx
Certbot
python3-certbot-nginx
dnsutils
curl
```

Docker runs the Spring Boot app container. Nginx handles public traffic. Certbot manages HTTPS.

### `scripts/nginx.sh`

Creates the Nginx reverse proxy configuration.

Usage:

```bash
sudo ./nginx.sh <APP_PORT> <DOMAIN>
```

Example:

```bash
sudo ./nginx.sh 8080 example.duckdns.org
```

It forwards traffic to the active local app container.

### `scripts/https.sh`

Configures HTTPS with Let's Encrypt using Certbot.

Usage:

```bash
sudo ./https.sh <DOMAIN> <EMAIL>
```

It enables HTTPS and redirects HTTP traffic to HTTPS.

### `scripts/deploy-app.sh`

Main deployment script.

It:

```text
Updates DuckDNS
Waits for DNS propagation
Runs VM setup
Uploads the JAR file
Detects the active blue/green container
Starts the new version on the inactive port
Runs a health check
Switches Nginx traffic to the new version
Removes the old container
Runs final HTTP/HTTPS checks
```

If the new version fails the health check, the deployment stops and Nginx is not switched. This protects the currently running version.

## Blue-Green Deployment

The application uses two local containers:

```text
myapp-blue  -> 127.0.0.1:8080
myapp-green -> 127.0.0.1:8081
```

Deployment process:

```text
1. Detect active container
2. Start new version on inactive port
3. Run health check
4. Switch Nginx to the new version
5. Remove old container
```

This keeps the previous version running while the new one starts.

## Security Measures

```text
Secrets are stored in GitLab CI/CD variables
Service account key is not committed
SSH keys are not committed
Database password is not committed
Only ports 22, 80 and 443 are public
Application ports 8080 and 8081 are local only
Cloud SQL allows access only from the VM IP
HTTP redirects to HTTPS
HTTPS uses Let's Encrypt
```

## Manual Cleanup

The manual destroy stage should be run after the working session.

This avoids unnecessary Google Cloud costs by removing:

```text
VM
Cloud SQL instance
Firewall rules
```
