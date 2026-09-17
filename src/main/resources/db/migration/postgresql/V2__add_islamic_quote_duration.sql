--
-- V2: per-quote slide duration.
--
-- Nullable with no default on purpose: null is the frame contract's "auto", so every existing
-- quote keeps the behaviour it had before this column existed rather than being backfilled to
-- a duration nobody chose.
--
-- The SQLite twin of this file must stay at the same version -- see docs/MIGRATIONS.md.

alter table islamic_quotes add column duration_seconds integer;
