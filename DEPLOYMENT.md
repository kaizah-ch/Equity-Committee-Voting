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

## Troubleshooting

If logs show `Connection to localhost:5432 refused`, the container did not receive the production datasource settings. Check:

```bash
cat .env
docker compose config
docker compose up -d --build
```

The datasource URL must point to the DB VPS, not `localhost`.

To inspect files inside the image, override the Java entrypoint:

```bash
docker run --rm --entrypoint ls equity-committee-voting_equity-voting-backend /app
```
