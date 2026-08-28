--
-- V8: give a prayer space a type, a pin on the map, and somewhere to put a photo,
--     and admit BOARD_PRAYER_SPACE_WRITE into the permission CHECK.
--
-- Two things were conflated before this. `audience` was doing double duty as both "who is
-- this listed for" and "what kind of room is it", which cannot describe the campus: a
-- multifaith room and a reflection bay are both shown to everyone, and are not the same
-- place. `space_type` now answers the second question and `audience` keeps the first.
-- Existing rows backfill from audience -- a BROTHERS row was a brothers' musallah -- with
-- BOTH landing on MULTIFAITH, since nothing in the seed data was a reflection bay under
-- the old shape. Anything mislabelled is one PATCH away.
--
-- `location` becomes `room_info`. It described where in the building the room is
-- ("Between offices 3026 & 3028"), which stops reading as "location" the moment the table
-- also holds latitude and longitude.
--
-- Coordinates are nullable and are written as a pair (the service rejects one without the
-- other): a reflection bay tucked inside a building has no useful pin, and a space can be
-- listed before anyone has stood in it with a phone.
--
-- The SQLite twin of this file must stay at the same version -- see docs/MIGRATIONS.md.


-- 1. Rename location -> room_info.
alter table prayer_spaces rename column location to room_info;


-- 2. Add space_type, backfilled from audience, then made NOT NULL.
alter table prayer_spaces add column space_type varchar(255);

update prayer_spaces
set space_type = case audience
                     when 'BROTHERS' then 'BROTHERS'
                     when 'SISTERS' then 'SISTERS'
                     else 'MULTIFAITH'
                 end;

alter table prayer_spaces alter column space_type set not null;

alter table prayer_spaces
    add constraint prayer_spaces_space_type_check
    check (space_type in ('BROTHERS','SISTERS','MULTIFAITH','REFLECTION'));


-- 3. Map pin, photo, and the free-text fields the app renders when no steps exist yet.
alter table prayer_spaces add column latitude float(53);
alter table prayer_spaces add column longitude float(53);
alter table prayer_spaces add column image_url varchar(255);
alter table prayer_spaces add column notes TEXT;
alter table prayer_spaces add column directions TEXT;


-- 4. Widen the user_permissions CHECK for BOARD_PRAYER_SPACE_WRITE.
--    Same discovery-then-replace pattern as V6 and V7: drop whatever CHECK currently
--    constrains the column (prod's names predate Flyway and are not guaranteed to match a
--    fresh database's), then add a named replacement.

do $$
declare
    constraint_name text;
begin
    for constraint_name in
        select con.conname
        from pg_constraint con
        join pg_class rel on rel.oid = con.conrelid
        where rel.relname = 'user_permissions'
          and con.contype = 'c'
    loop
        execute format('alter table user_permissions drop constraint %I', constraint_name);
    end loop;
end $$;

alter table user_permissions
    add constraint user_permissions_permission_check check (permission in (
        'MEDIA_UPLOAD_SELF','MEDIA_UPLOAD_MODERATE','MEDIA_UPLOAD_READ',
        'BOARD_CONTENT_READ','BOARD_POSTER_WRITE','BOARD_EVENT_WRITE','BOARD_SOCIAL_WRITE',
        'BOARD_WEEKLY_WRITE','BOARD_TICKER_WRITE','BOARD_PRAYER_SPACE_WRITE',
        'BOARD_CONFIG_READ','BOARD_CONFIG_WRITE','BOARD_REFRESH',
        'BOARD_DEVICE_READ','BOARD_DEVICE_ENROLL','BOARD_DEVICE_REVOKE','BOARD_TELEMETRY_SUBSCRIBE',
        'BOARD_COMMAND_BENIGN','BOARD_COMMAND_DISRUPTIVE','BOARD_COMMAND_INSPECT',
        'TCKET_SCAN','TCKET_MANAGE','TCKET_ADMIN',
        'IAM_USER_READ','IAM_USER_WRITE','IAM_ROLE_GRANT','AUDIT_READ'));
