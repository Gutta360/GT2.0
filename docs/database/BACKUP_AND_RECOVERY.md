# Backup and recovery

## Two independent backup layers

1. Tablet logical snapshots provide an application-controlled clean-device recovery artifact.
2. Operations must back up PostgreSQL and `/var/lib/gt2/snapshots` together to encrypted off-host storage. Snapshot metadata without its file, or a file without the matching database row and master key, is insufficient.

## Snapshot security

- Client computes lowercase SHA-256 over exact UTF-8 JSON.
- Server validates the checksum, contract, patient fields, and duplicate patient IDs.
- Files use AES-256-GCM with a random nonce and tenant/snapshot authenticated data.
- `GT2_SNAPSHOT_MASTER_KEY` is Base64 for exactly 32 random bytes and must be backed up separately in the server secret manager.
- Default maximum upload is 10 MiB and default retention is five available snapshots per tenant.
- Local logins and device bearer tokens are excluded.

Generate a key with `openssl rand -base64 32`. Loss of this key makes snapshots unrecoverable. Reusing it across unrelated deployments increases blast radius.

## Controlled tablet recovery drill

1. Confirm a recent snapshot exists and record its SHA-256 and patient count.
2. On a clean/test Android installation, create the local login and register the device with a fresh enrollment code.
3. Open Sync, select the snapshot, and choose Restore. The server overlays the selected baseline with later accepted patient versions, choosing the highest version for each patient.
4. The app confirms the local patient table is empty, downloads the generated package through authenticated HTTPS, verifies SHA-256, and inserts all rows in one transaction.
5. Verify patient count, latest edited values, deleted-patient behavior, and next generated patient number.
6. Confirm a `RESTORE/SUCCESS` audit row. Preserve evidence in the test-results document.

Never test destructive recovery against the only production tablet. A PostgreSQL restore drill must use an isolated database and restored copy of the snapshot directory. RPO/RTO and backup scheduling remain deployment-owner decisions.
