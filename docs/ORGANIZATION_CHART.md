# Organization chart artifact

The organization chart is a required project artifact. New projects receive one
`ProjectRequiredArtifact` row with type `ORGANIZATION_CHART`, name `조직도`, and
required version `1.0`. Status lookup and chart generation repair the row for
older projects without creating duplicates.

## Flow

1. The project PM requests generation.
2. The backend loads the project, confirmed leaf WBS tasks, schedules, active
   members, capability profiles, and the project PM.
3. The planning AI endpoint returns validated organization JSON and a
   transfer-only Base64 JPG.
4. The backend validates project ID, Base64, content type, dimensions, byte
   size, and JPEG magic bytes.
5. The JPG is stored privately through `DocumentObjectStorage`.
6. A new `ProjectDocument` and a `PENDING` `ProjectArtifact` version are saved.
   Existing versions remain unchanged.

If database persistence fails after upload, the newly uploaded S3 object is
deleted. A project row lock serializes version calculation, including numeric
versions such as `1.10` and `1.11`.

## API

```http
POST /api/projects/{projectId}/artifacts/organization-chart/generate
GET  /api/projects/{projectId}/artifacts/organization-chart/latest
GET  /api/projects/{projectId}/artifacts/organization-chart/latest/download
```

Generation returns `201 Created`; metadata and download return `200 OK`.
Metadata contains only the authenticated backend preview/download path. It does
not contain Base64, an S3 object key, or a public S3 URL.

```json
{
  "artifactId": 10,
  "projectId": 1,
  "artifactType": "ORGANIZATION_CHART",
  "artifactName": "조직도",
  "version": "1.0",
  "approvalStatus": "PENDING",
  "contentType": "image/jpeg",
  "fileSize": 182300,
  "generatedAt": "2026-08-05T10:00:00",
  "previewUrl": "/api/projects/1/artifacts/organization-chart/latest/download",
  "downloadUrl": "/api/projects/1/artifacts/organization-chart/latest/download"
}
```

## Prerequisites and permissions

Generation requires the owning PM, confirmed leaf WBS tasks, schedules for all
leaf tasks, active project members, and capability profiles for every active
member. Missing prerequisites return `409` with a specific code:

- `CONFIRMED_WBS_NOT_FOUND`
- `PLANNING_SCHEDULE_NOT_FOUND`
- `ACTIVE_PROJECT_MEMBER_NOT_FOUND`
- `MEMBER_CAPABILITY_NOT_FOUND`

The owning PM may generate or regenerate. The owning PM and active project
members may read metadata, preview, and download. Other authenticated users
receive `403`; unauthenticated requests receive `401`.

## Storage and operation

Objects use this private key pattern:

```text
projects/{projectId}/artifacts/organization-chart/{uuid}.jpg
```

The existing S3 IAM role and `AWS_S3_BUCKET` configuration are reused. No AWS
access key is added. The AI server must have a Korean-capable Noto Sans CJK font
and a valid `ORG_CHART_FONT_PATH` in its environment. The backend planning
client path can be overridden with
`PLANNING_AGENT_ORGANIZATION_CHART_PATH`; its default is the documented AI API.

Local verification:

```bash
./gradlew clean test --no-daemon --max-workers=1
./gradlew clean build --no-daemon --max-workers=1
```

Figma generation and Figma credentials are intentionally outside this feature.
