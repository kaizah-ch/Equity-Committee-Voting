# VPS Deployment

Use these commands from the project root on the VPS.

```bash
git pull
cp .env.example .env
nano .env
docker compose up -d --build
docker logs -f equity-voting-backend
curl http://localhost:5010/api/v1/platform/health
```

The PostgreSQL database must already be reachable from the backend container using the `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD` values in `.env`.

Do not commit `.env` or any real credentials.
