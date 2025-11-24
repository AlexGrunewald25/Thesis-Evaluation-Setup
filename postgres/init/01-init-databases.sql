-- postgres/init/01-init-databases.sql

-- Benutzer für Claims
CREATE USER claims_user WITH ENCRYPTED PASSWORD 'claims_password';

-- Benutzer für Policies
CREATE USER policy_user WITH ENCRYPTED PASSWORD 'policy_password';

-- Datenbanken
CREATE DATABASE claimsdb OWNER claims_user;
CREATE DATABASE policydb OWNER policy_user;

-- (optional) Rechte noch einmal explizit setzen
GRANT ALL PRIVILEGES ON DATABASE claimsdb TO claims_user;
GRANT ALL PRIVILEGES ON DATABASE policydb TO policy_user;
