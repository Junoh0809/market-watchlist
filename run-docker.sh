#!/usr/bin/env bash
set -euo pipefail

BACKEND_IMAGE="stock-management-backend"
FRONTEND_IMAGE="stock-management-frontend"
BACKEND_CONTAINER="stock-management-backend"
FRONTEND_CONTAINER="stock-management-frontend"
APP_NETWORK="stock-management-network"
DB_CONTAINER="stock-management-db"
DB_VOLUME="stock-management-data"
DB_PASSWORD="${STOCK_DB_PASSWORD:-stocks}"

# Docker Desktop stores its credential helper here on macOS. Some shells do
# not include this directory in PATH even though Docker itself is installed.
if [[ -d "${HOME}/.docker/bin" ]]; then
  export PATH="${HOME}/.docker/bin:${PATH}"
fi

if ! docker info >/dev/null 2>&1; then
  echo "Docker daemon에 연결할 수 없습니다. Docker Desktop 또는 Docker Engine을 먼저 시작하세요." >&2
  echo "확인 명령: docker info" >&2
  exit 1
fi

echo "Building backend image..."
docker build -t "$BACKEND_IMAGE" .

echo "Building frontend image..."
docker build --build-arg VITE_API_URL=/api/v1 -t "$FRONTEND_IMAGE" ./frontend

if ! docker network inspect "$APP_NETWORK" >/dev/null 2>&1; then
  docker network create "$APP_NETWORK" >/dev/null
fi

docker volume create "$DB_VOLUME" >/dev/null
if docker container inspect "$DB_CONTAINER" >/dev/null 2>&1; then
  docker start "$DB_CONTAINER" >/dev/null
else
  docker run -d --name "$DB_CONTAINER" --network "$APP_NETWORK" --network-alias db \
    -e POSTGRES_DB=stocks -e POSTGRES_USER=stocks -e POSTGRES_PASSWORD="$DB_PASSWORD" \
    -p 127.0.0.1:5433:5432 -v "$DB_VOLUME":/var/lib/postgresql/data postgres:16-alpine >/dev/null
fi

for attempt in {1..60}; do
  if docker exec "$DB_CONTAINER" pg_isready -U stocks -d stocks >/dev/null 2>&1; then
    break
  fi
  if [[ "$attempt" == 60 ]]; then
    echo "PostgreSQL이 준비되지 않았습니다. docker logs $DB_CONTAINER 로 확인하세요." >&2
    exit 1
  fi
  sleep 1
done

echo "Starting backend on http://localhost:8080 ..."
docker rm -f "$BACKEND_CONTAINER" >/dev/null 2>&1 || true
docker run -d --rm --name "$BACKEND_CONTAINER" --network "$APP_NETWORK" --network-alias backend \
  -e STOCK_MOCK_QUOTE_SCENARIO="${STOCK_MOCK_QUOTE_SCENARIO:-NORMAL}" \
  -e STOCK_DB_URL=jdbc:postgresql://db:5432/stocks -e STOCK_DB_USER=stocks \
  -e STOCK_DB_PASSWORD="$DB_PASSWORD" -p 8080:8080 "$BACKEND_IMAGE" >/dev/null

echo "Starting frontend on http://localhost:3000 ..."
docker rm -f "$FRONTEND_CONTAINER" >/dev/null 2>&1 || true
docker run -d --rm --name "$FRONTEND_CONTAINER" --network "$APP_NETWORK" -p 3000:80 "$FRONTEND_IMAGE" >/dev/null

echo "App is running: http://localhost:3000"
echo "Stop with: docker stop $FRONTEND_CONTAINER $BACKEND_CONTAINER $DB_CONTAINER"
