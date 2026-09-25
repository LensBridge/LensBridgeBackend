--
-- V3: the board's latest report of itself (heartbeat `telemetry.board`): the app it runs, the
-- content it shows, updates waiting for the night, the last agent update, whether its clock
-- can be believed. One JSON document per device, replaced on every heartbeat, so a column
-- rather than a table: it is current state, not history (device_telemetry keeps history).
--
-- Nullable: boards report it from agent 0.3.0 on.
--
-- The Postgres twin of this file must stay at the same version -- see docs/MIGRATIONS.md.

alter table devices add column board_report TEXT;
alter table devices add column board_report_at timestamp;
