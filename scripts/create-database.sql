-- Run once as a PostgreSQL superuser:  psql -U postgres -f scripts/create-database.sql
-- Flyway creates every table on first start; this only creates the empty database.
SELECT 'CREATE DATABASE agrilink ENCODING ''UTF8'''
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'agrilink')\gexec
