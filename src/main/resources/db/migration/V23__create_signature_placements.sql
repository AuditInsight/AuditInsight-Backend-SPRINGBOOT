-- Free-position signature placement (e-signing Phase 2).
-- Each row is one box where a signature is stamped onto the document. Coordinates are fractions (0..1) of the
-- page as it is displayed (after the page's /Rotate is applied), measured from the top-left corner, so they are
-- independent of zoom level and screen size. page_index is 0-based.

CREATE TABLE evidence_signature_placements (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    signature_id UUID         NOT NULL REFERENCES evidence_signatures(id) ON DELETE CASCADE,
    page_index   INT          NOT NULL CHECK (page_index >= 0),
    x            NUMERIC(8,6) NOT NULL CHECK (x >= 0 AND x <= 1),
    y            NUMERIC(8,6) NOT NULL CHECK (y >= 0 AND y <= 1),
    width        NUMERIC(8,6) NOT NULL CHECK (width > 0 AND width <= 1),
    height       NUMERIC(8,6) NOT NULL CHECK (height > 0 AND height <= 1),
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_signature_placements_signature ON evidence_signature_placements (signature_id);

-- SHA-256 of the exact document the signer was looking at when placing the signature (audit record).
ALTER TABLE evidence_signatures ADD COLUMN base_sha256 CHAR(64);
