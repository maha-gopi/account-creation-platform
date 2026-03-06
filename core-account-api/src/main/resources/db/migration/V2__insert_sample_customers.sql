-- Flyway migration V2: Insert sample customer data for testing
-- This data is for development and testing purposes

INSERT INTO acct_owner.customer (customer_id, customer_name, date_of_birth, status, blacklist_flag, country_code)
VALUES
    ('CUST00000001', 'John Smith', '1980-05-15', 'A', 'N', 'US'),
    ('CUST00000002', 'Jane Doe', '1985-08-22', 'A', 'N', 'US'),
    ('CUST00000003', 'Robert Johnson', '1975-03-10', 'A', 'N', 'GB'),
    ('CUST00000004', 'Maria Garcia', '1990-12-01', 'A', 'N', 'ES'),
    ('CUST00000005', 'Ahmed Hassan', '1988-07-18', 'I', 'N', 'EG'),
    ('CUST00000006', 'Priya Sharma', '1992-09-25', 'A', 'Y', 'IN'),
    ('CUST00000007', 'Michael Brown', '1978-11-30', 'A', 'N', 'CA'),
    ('CUST00000008', 'Liu Wei', '1995-04-12', 'A', 'N', 'CN'),
    ('CUST00000009', 'Anna Kowalski', '1983-06-08', 'A', 'N', 'PL'),
    ('CUST00000010', 'Carlos Silva', '1987-02-20', 'A', 'N', 'BR')
ON CONFLICT (customer_id) DO NOTHING;

COMMENT ON TABLE acct_owner.customer IS 'Sample customer data - CUST00000005 is inactive, CUST00000006 is blacklisted';
