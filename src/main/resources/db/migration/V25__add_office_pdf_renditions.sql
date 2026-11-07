-- Word/Excel signing by conversion (e-signing follow-up).
-- A Word/Excel document is converted to PDF once (by Gotenberg); that PDF is stored in Cloudinary and becomes the
-- page geometry signers place signatures on. The original Office file stays untouched as version 0.
ALTER TABLE evidence
    ADD COLUMN pdf_rendition_url    TEXT,
    ADD COLUMN pdf_rendition_sha256 CHAR(64);

-- What a stored signed version is: the document with signatures stamped on it, or a standalone signature
-- certificate (how Word/Excel files were signed before conversion existed). A document keeps the kind it started
-- with, so certificates already handed out stay consistent.
ALTER TABLE evidence_document_versions
    ADD COLUMN rendition VARCHAR(20) NOT NULL DEFAULT 'DOCUMENT' CHECK (rendition IN ('DOCUMENT', 'CERTIFICATE'));

UPDATE evidence_document_versions v
   SET rendition = 'CERTIFICATE'
  FROM evidence e
 WHERE e.id = v.evidence_id
   AND lower(e.file_type) IN ('docx', 'xlsx', 'xls');
