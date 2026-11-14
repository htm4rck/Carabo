CREATE TABLE IF NOT EXISTS carabo.fiscal_route_revision (
 id uuid PRIMARY KEY, business_code varchar(50) NOT NULL REFERENCES carabo.tenant(business_code) ON DELETE RESTRICT,
 version integer NOT NULL CHECK(version>0), routes_json text NOT NULL, created_at timestamptz NOT NULL, actor_code varchar(100) NOT NULL,
 UNIQUE(business_code,version));
ALTER TABLE carabo.document ADD COLUMN IF NOT EXISTS fiscal_route_snapshot text;
-- Keep the existing per-document provider. Endpoint and immutable managed references survive future tenant edits.
UPDATE carabo.document d SET fiscal_route_snapshot=json_build_object('version',0,'provider',COALESCE(d.send_mode,t.send_mode,'SUNAT_DIRECT'),
 'endpoint',CASE WHEN d.send_mode='OSE' THEN t.ose_base_url ELSE COALESCE(t.sunat_endpoint,CASE WHEN t.environment='PRODUCTION' THEN 'https://e-factura.sunat.gob.pe/ol-ti-itcpfegem/billService' ELSE 'https://e-beta.sunat.gob.pe/ol-ti-itcpfegem-beta/billService' END) END,
 'user',t.sol_user,'credential',CASE WHEN d.send_mode='OSE' AND t.ose_api_token LIKE 'secret://%' THEN t.ose_api_token
 WHEN d.send_mode<>'OSE' AND t.sol_password LIKE 'secret://%' THEN t.sol_password ELSE NULL END)::text
 FROM carabo.tenant t WHERE t.business_code=d.business_code AND d.fiscal_route_snapshot IS NULL;
CREATE OR REPLACE FUNCTION carabo.reject_fiscal_route_revision_mutation() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Fiscal route revisions are immutable'; END $$;
CREATE TRIGGER fiscal_route_revision_immutable BEFORE UPDATE OR DELETE ON carabo.fiscal_route_revision FOR EACH ROW EXECUTE FUNCTION carabo.reject_fiscal_route_revision_mutation();
-- Rollback: drop trigger/function/table and fiscal_route_snapshot only after draining documents created with this release.
