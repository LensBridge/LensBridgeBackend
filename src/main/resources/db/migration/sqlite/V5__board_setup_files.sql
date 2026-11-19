--
-- V5: boards created in the console before they ever connect (offline-first, v3).
--
--   devices.claim_secret        32 random bytes, the secret of the board's online claim
--                               (POST /api/agent/claim). The backend must verify an HMAC made
--                               with it, so it is stored as is, not hashed. It is a one-use
--                               bearer credential: it only lets a board bind its device key to
--                               this not-yet-bound device. It leaves the server only inside an
--                               online setup file, and a new online setup file replaces it.
--                               Null for a board that has never had an online setup file.
--   devices.provision_sequence  sequence of the last setup file issued for the device; the next
--                               one must be greater, since a board only accepts a newer one.
--   enrollment_tokens.device_id the pre-created device a token enrolls (binds a key to), or null
--                               for a legacy token that creates a new device.
--
-- The Postgres twin of this file must stay at the same version -- see MigrationParityTest.

alter table devices add column claim_secret blob;
alter table devices add column provision_sequence bigint;
alter table enrollment_tokens add column device_id blob;
