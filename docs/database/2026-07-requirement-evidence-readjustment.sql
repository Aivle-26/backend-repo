-- The project currently uses Hibernate ddl-auto=update and has no Flyway/Liquibase runtime.
-- This idempotent MySQL DDL is the explicit production review script for the
-- evidence/readjustment tables and active requirement-reference uniqueness.

SET @active_requirement_reference_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'project_requirements'
      AND column_name = 'active_external_reference_id'
);
SET @active_requirement_reference_column_ddl = IF(
    @active_requirement_reference_column_exists = 0,
    'ALTER TABLE project_requirements ADD COLUMN active_external_reference_id BIGINT NULL AFTER external_reference_id',
    'SELECT 1'
);
PREPARE active_requirement_reference_column_statement
    FROM @active_requirement_reference_column_ddl;
EXECUTE active_requirement_reference_column_statement;
DEALLOCATE PREPARE active_requirement_reference_column_statement;

UPDATE project_requirements
SET active_external_reference_id = CASE
    WHEN included_in_final = TRUE THEN external_reference_id
    ELSE NULL
END;

-- Preflight: this result set must be empty. The following ALTER intentionally
-- stops if duplicates have not been resolved, while legacy uniqueness remains.
SELECT project_id, active_external_reference_id, COUNT(*) AS duplicate_count
FROM project_requirements
WHERE active_external_reference_id IS NOT NULL
GROUP BY project_id, active_external_reference_id
HAVING COUNT(*) > 1;

SET @active_requirement_reference_constraint_exists = (
    SELECT COUNT(*)
    FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name = 'project_requirements'
      AND constraint_name = 'uk_project_requirement_active_external_reference'
      AND constraint_type = 'UNIQUE'
);
SET @active_requirement_reference_constraint_ddl = IF(
    @active_requirement_reference_constraint_exists = 0,
    'ALTER TABLE project_requirements ADD CONSTRAINT uk_project_requirement_active_external_reference UNIQUE (project_id, active_external_reference_id)',
    'SELECT 1'
);
PREPARE active_requirement_reference_constraint_statement
    FROM @active_requirement_reference_constraint_ddl;
EXECUTE active_requirement_reference_constraint_statement;
DEALLOCATE PREPARE active_requirement_reference_constraint_statement;

-- Drop retired direct uniqueness only after active uniqueness is in place.
SET @legacy_requirement_reference_constraint_exists = (
    SELECT COUNT(*)
    FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name = 'project_requirements'
      AND constraint_name = 'uk_project_requirement_external_reference'
      AND constraint_type = 'UNIQUE'
);
SET @legacy_requirement_reference_constraint_ddl = IF(
    @legacy_requirement_reference_constraint_exists = 1,
    'ALTER TABLE project_requirements DROP INDEX uk_project_requirement_external_reference',
    'SELECT 1'
);
PREPARE legacy_requirement_reference_constraint_statement
    FROM @legacy_requirement_reference_constraint_ddl;
EXECUTE legacy_requirement_reference_constraint_statement;
DEALLOCATE PREPARE legacy_requirement_reference_constraint_statement;

CREATE TABLE IF NOT EXISTS project_requirement_evidences (
    id BIGINT NOT NULL AUTO_INCREMENT,
    requirement_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    page_number INT NULL,
    chunk_id VARCHAR(255) NOT NULL,
    quote_text TEXT NOT NULL,
    start_offset INT NULL,
    end_offset INT NULL,
    bounding_boxes_json TEXT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_requirement_evidence_requirement (requirement_id),
    INDEX idx_requirement_evidence_document (document_id),
    CONSTRAINT fk_requirement_evidence_requirement
        FOREIGN KEY (requirement_id) REFERENCES project_requirements (id),
    CONSTRAINT fk_requirement_evidence_document
        FOREIGN KEY (document_id) REFERENCES project_documents (id)
);

CREATE TABLE IF NOT EXISTS project_requirement_change_candidates (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    existing_requirement_id BIGINT NULL,
    change_type VARCHAR(20) NOT NULL,
    review_status VARCHAR(30) NOT NULL,
    change_reason VARCHAR(1000) NOT NULL,
    existing_requirement_json TEXT NULL,
    proposed_requirement_json TEXT NULL,
    evidences_json TEXT NULL,
    base_requirement_updated_at DATETIME(6) NULL,
    reviewed_at DATETIME(6) NULL,
    applied_at DATETIME(6) NULL,
    version BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_requirement_change_project (project_id),
    INDEX idx_requirement_change_existing (existing_requirement_id),
    CONSTRAINT fk_requirement_change_project
        FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_requirement_change_existing
        FOREIGN KEY (existing_requirement_id) REFERENCES project_requirements (id)
);
