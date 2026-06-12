#!/usr/bin/env bash
# Local dev runner for breviare. Starts the app against a local Postgres container.
#
# First time:
#   1. Start Postgres (needs docker access; if you're not in the 'docker' group, prefix with sudo):
#        docker run -d --name breviare-pg -e POSTGRES_USER=breviare -e POSTGRES_PASSWORD=breviare -e POSTGRES_DB=breviare -p 5433:5432 postgres:16
#   2. cp .env.example .env  &&  edit JWT_SECRET (openssl rand -hex 32)
#   3. ./run-local.sh
set -euo pipefail
cd "$(dirname "$0")"

if [[ -f .env ]]; then
  set -a; . ./.env; set +a
else
  echo ".env not found — copy .env.example to .env first." >&2
  exit 1
fi

export JAVA_HOME="${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")}"
export TZ=UTC
exec ./mvnw spring-boot:run
