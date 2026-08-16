--
-- V5: BoardEvent.event, the optional link from a board's own event to its tCketManage
-- counterpart in "tcket:events" (created by tcketmanage-core's own migration -- see
-- TcketManageFlywayConfig for why that runs under a separate Flyway history and is not
-- duplicated here).
--
-- No FK here -- board_events already exists, and SQLite can't ADD CONSTRAINT to an existing
-- table (see docs/MIGRATIONS.md's "Known dev/prod divergences").
--
-- The postgres twin of this file must stay at the same version -- see docs/MIGRATIONS.md.

alter table board_events add column event_id blob;
