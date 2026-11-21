--
-- V4: room for real-world board text.
--
-- The Postgres twin of this file widens the free-text columns of islamic_quotes,
-- promotable_social_media and board_config_messages from varchar(255). SQLite does not
-- enforce the length in a varchar(N) declaration, so there is nothing to change here; this
-- file exists so both vendors carry the same version set (MigrationParityTest).
--
-- The Postgres twin of this file must stay at the same version -- see docs/MIGRATIONS.md.

select 1;
