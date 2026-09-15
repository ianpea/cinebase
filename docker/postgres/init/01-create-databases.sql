-- Runs once, the first time the postgres-data volume is initialised.
--
-- The image's POSTGRES_DB variable creates movie_service. person-service owns a
-- separate database, so it is created here. The two services never query each
-- other's tables; they only exchange data over gRPC.
SELECT 'CREATE DATABASE person_service'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'person_service')\gexec
