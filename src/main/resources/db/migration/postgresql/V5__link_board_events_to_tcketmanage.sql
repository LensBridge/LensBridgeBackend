--
-- V5: BoardEvent.event, the optional link from a board's own event to its tCketManage
-- counterpart in "tcket:events" (created by tcketmanage-core's own migration -- see
-- TcketManageFlywayConfig for why that runs under a separate Flyway history and is not
-- duplicated here).
--
-- The SQLite twin of this file must stay at the same version -- see docs/MIGRATIONS.md.

alter table board_events add column event_id uuid;
alter table board_events add constraint fk_board_events_event_id foreign key (event_id) references "tcket:events";
