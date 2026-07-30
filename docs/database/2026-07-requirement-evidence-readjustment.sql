-- The project currently uses Hibernate ddl-auto=update and has no Flyway/Liquibase runtime.
-- This idempotent MySQL DDL is the explicit production review script for the two new tables.

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
