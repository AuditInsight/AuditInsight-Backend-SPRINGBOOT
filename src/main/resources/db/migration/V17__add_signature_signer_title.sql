-- Optional job title the signer enters when signing (e.g. Finance Manager).
ALTER TABLE evidence_signatures ADD COLUMN signer_title VARCHAR(255);
