-- Every call to the AI is one line of ai_call, the only ledger of what the AI
-- cost: the analyses of a consultation file write there too, one line per
-- attempt, and the totals read nothing else, so a call is counted once.
-- Each line knows its tender (when there is one), its analysis, how it ended
-- and its cost, priced when it was made: estimated from the tokens and the
-- price grid, since the provider does not return the price of a call.
-- Rollback: DELETE FROM ai_call WHERE analysis_id IS NOT NULL; (before, no line had an analysis)
--   ALTER TABLE ai_call DROP COLUMN tender_id, DROP COLUMN analysis_id, DROP COLUMN status,
--   DROP COLUMN error_kind, DROP COLUMN cost, DROP COLUMN cost_confirmed;
ALTER TABLE ai_call
    ADD COLUMN tender_id      BIGINT REFERENCES tender (id) ON DELETE SET NULL,
    ADD COLUMN analysis_id    BIGINT REFERENCES analysis (id) ON DELETE SET NULL,
    -- RUNNING, DONE, FAILED, INTERRUPTED (the server stopped during the call)
    ADD COLUMN status         VARCHAR(12)    NOT NULL DEFAULT 'DONE',
    ADD COLUMN error_kind     VARCHAR(30),
    ADD COLUMN cost           NUMERIC(14, 6),
    ADD COLUMN cost_confirmed BOOLEAN        NOT NULL DEFAULT false;

-- The analyses made before: their last attempt, the only one whose tokens
-- were kept. Their cost is priced at the next start, with the grid of then.
INSERT INTO ai_call (kind, model, input_tokens, output_tokens, created_by, created_at, tender_id, analysis_id,
                     status, error_kind)
SELECT a.kind, a.model, a.input_tokens, coalesce(a.output_tokens, 0), a.requested_by,
       coalesce(a.finished_at, a.created_at), a.tender_id, a.id,
       CASE WHEN a.status = 'DONE' THEN 'DONE' WHEN a.status = 'CANCELLED' THEN 'INTERRUPTED' ELSE 'FAILED' END,
       a.error_kind
FROM analysis a
WHERE a.input_tokens IS NOT NULL;

CREATE INDEX idx_ai_call_tender ON ai_call (tender_id) WHERE tender_id IS NOT NULL;
CREATE INDEX idx_ai_call_kind_created ON ai_call (kind, created_at);
