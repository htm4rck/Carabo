-- Idempotent DEV-only tenant. Fiscal credentials remain intentionally empty.
INSERT INTO carabo.tenant (
  business_code, business_name, issuer_ruc, issuer_name, issuer_trade,
  issuer_address, send_mode, environment, country_code, api_key, active
)
SELECT
  b."businessCode", b."businessName", b.nif, b.corporation, b."businessName",
  b."addressStreet", 'SUNAT_DIRECT', 'BETA', COALESCE(b."countryCode", 'PE'),
  encode(gen_random_bytes(32), 'hex'), true
FROM setup.business b
WHERE b."businessCode" = 'B20999999990'
ON CONFLICT (business_code) DO UPDATE SET
  business_name = EXCLUDED.business_name,
  issuer_ruc = EXCLUDED.issuer_ruc,
  issuer_name = EXCLUDED.issuer_name,
  issuer_trade = EXCLUDED.issuer_trade,
  issuer_address = EXCLUDED.issuer_address,
  country_code = EXCLUDED.country_code,
  active = true,
  updated_at = now();
