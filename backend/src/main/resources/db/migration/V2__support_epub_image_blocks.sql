ALTER TABLE paragraphs
    ADD COLUMN block_type VARCHAR(20) NOT NULL DEFAULT 'TEXT' AFTER paragraph_order;

ALTER TABLE paragraphs
    ADD COLUMN image_url VARCHAR(500) NULL AFTER furigana_text;
