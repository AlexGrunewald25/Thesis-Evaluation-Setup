-- policy-service/src/main/resources/data.sql

-- Beispiel-Policen für Tests
-- WICHTIG: IDs entsprechen den PolicyIds, die wir im Claim-/k6-Test verwenden

INSERT INTO policies (id, policy_number, product_code, status, valid_from, valid_to)
VALUES (
    '11111111-1111-1111-1111-111111111111',
    'POLICY-TEST-001',
    'HOUSEHOLD',
    'ACTIVE',
    '2023-01-01',
    '2026-12-31'
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO policies (id, policy_number, product_code, status, valid_from, valid_to)
VALUES (
    '22222222-2222-2222-2222-222222222222',
    'POLICY-TEST-002',
    'CAR',
    'ACTIVE',
    '2022-01-01',
    '2027-12-31'
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO policies (id, policy_number, product_code, status, valid_from, valid_to)
VALUES (
    '33333333-3333-3333-3333-333333333333',
    'POLICY-TEST-003',
    'TRAVEL',
    'TERMINATED',
    '2019-01-01',
    '2021-12-31'
)
ON CONFLICT (id) DO NOTHING;
