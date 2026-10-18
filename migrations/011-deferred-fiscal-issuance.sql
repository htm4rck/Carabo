-- Accepted intents may wait for tenant fiscal configuration without consuming a correlativo.
ALTER TABLE carabo.document ALTER COLUMN number DROP NOT NULL;
ALTER TABLE carabo.document ALTER COLUMN status TYPE VARCHAR(32);
