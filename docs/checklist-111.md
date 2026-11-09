# 111-item backend / system-design checklist: Logoxels (status 2026-11-09)

Verified in repo: CI timeout and concurrency added, signing key read from secrets, never committed.
Done: UI font pinned to a commit and checksum-verified; server/deploy.php is cron-only, HTTPS-only and verifies the GitHub asset digest before publishing. Open: one GitHub release is created per build (add cleanup). Not applicable: rate limits, CORS and input validation on server/ (no web endpoint).
Not applicable: sharding, replication, queues, API gateway, load balancing (local vector editor).
