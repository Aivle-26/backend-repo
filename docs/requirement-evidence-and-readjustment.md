# Requirement Evidence and Readjustment

## Runtime contract

- Existing initial analysis remains `POST /api/projects/{projectId}/requirements/analyze`.
- The backend sends `document_manifest` only when persisted document IDs are available.
- Evidence is stored in `project_requirement_evidences`.
- PDF bytes are streamed through
  `GET /api/projects/{projectId}/documents/{documentId}/content`.
- The backend validates project access before reading the S3 object.
- No S3 URL or object key is exposed to the AI server.

## Human review flow

1. `POST /api/projects/{projectId}/requirements/readjust` creates candidates.
2. `GET /api/projects/{projectId}/requirements/readjustments` lists candidates.
3. `PUT /api/projects/{projectId}/requirements/readjustments/{candidateId}`
   edits and approves or rejects one candidate.
4. `POST /api/projects/{projectId}/requirements/readjustments/apply` applies
   only approved candidate IDs.

Candidates use optimistic versioning. Apply also locks selected candidate rows
and the referenced requirement row, then compares the requirement `updatedAt`
value captured at candidate creation. Candidate-level evidence is snapshotted
separately so an explicit removal keeps the new document's removal quote. A
second apply is idempotent and does not create duplicate rows.

## Schema rollout

This repository does not run Flyway or Liquibase. Production currently uses
`JPA_DDL_AUTO=update`. Review
`docs/database/2026-07-requirement-evidence-readjustment.sql` before rollout.
Either apply that DDL explicitly or allow the existing Hibernate update policy
to create the same tables, but do not do both concurrently.
