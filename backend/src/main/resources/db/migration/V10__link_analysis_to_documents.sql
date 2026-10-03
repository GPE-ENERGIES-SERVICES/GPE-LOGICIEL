-- The tender is kept on the analysis to find its latest one directly.
ALTER TABLE analysis ADD COLUMN tender_id BIGINT NOT NULL REFERENCES tender (id);

-- Fingerprint of what was analysed (documents, model, instructions): the
-- same content is not analysed twice unless a user asks for it.
ALTER TABLE analysis ADD COLUMN input_fingerprint VARCHAR(64) NOT NULL;

CREATE INDEX idx_analysis_tender ON analysis (tender_id);

-- Documents given to the model: the summary lists what was read and what
-- was not.
CREATE TABLE analysis_document (
    analysis_id  BIGINT  NOT NULL REFERENCES analysis (id),
    document_id  BIGINT  NOT NULL REFERENCES document (id),
    PRIMARY KEY (analysis_id, document_id)
);
