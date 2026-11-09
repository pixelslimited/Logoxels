# 111-item backend / system-design checklist: Logoxels (status 2026-11-09)

Verified in repo: CI timeout and concurrency added, signing key read from secrets, never committed.
Open: the UI font is fetched from a branch URL at build time (pin it to a commit and checksum it); one GitHub release is created per build (add cleanup); the `server/` folder needs the same checks as Usrah (rate limits, input validation, secrets, TLS, CORS), not reviewed here.
Not applicable: sharding, replication, queues, API gateway, load balancing (local vector editor).
