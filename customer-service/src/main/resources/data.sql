-- Beispielkunde mit vollständigen Daten
INSERT INTO customers (
    id,
    customer_number,
    first_name,
    last_name,
    street,
    postal_code,
    city,
    email,
    phone_number
) VALUES (
    '11111111-1111-1111-1111-111111111111',
    'CUST-TEST-001',
    'Max',
    'Muster',
    'Hauptstraße 1',
    '10115',
    'Berlin',
    'max.muster@example.com',
    '+49-30-1234567'
) ON CONFLICT (id) DO NOTHING;

-- Beispielkunde mit unvollständiger Adresse
INSERT INTO customers (
    id,
    customer_number,
    first_name,
    last_name,
    street,
    postal_code,
    city,
    email,
    phone_number
) VALUES (
    '22222222-2222-2222-2222-222222222222',
    'CUST-TEST-002',
    'Erika',
    'Mustermann',
    NULL,
    '20095',
    'Hamburg',
    'erika.mustermann@example.com',
    NULL
) ON CONFLICT (id) DO NOTHING;
