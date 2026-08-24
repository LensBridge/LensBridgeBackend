--
-- V7: fold legacy media events into board events and retire the ADMIN role.
--
-- MediaEvent ("events" table) and BoardEvent ("board_events") are unified into a single
-- entity. The gallery frontend is retired; the upload pipeline stays and wires to
-- board_events via a new allow_uploads flag. The ADMIN role existed solely for media
-- moderation and is removed; its useful permissions (media:upload:moderate/read) move to
-- BOARD_ADMIN's bundle in Java — the DB only needs to remove the role from anyone holding
-- it.
--
-- The SQLite twin of this file must stay at the same version — see docs/MIGRATIONS.md.


-- 1. Add allow_uploads to board_events
alter table board_events add column allow_uploads boolean not null default false;


-- 2. Repoint uploads.event_id from the "events" table to "board_events".
--    Existing values reference UUIDs in "events" which is about to be dropped,
--    so null them out first.
update uploads set event_id = null;
alter table uploads drop constraint fk_uploads_event_id;
drop table events;
alter table uploads add constraint fk_uploads_board_event_id
    foreign key (event_id) references board_events(id);


-- 3. Remove the ADMIN role from anyone holding it.
delete from user_roles where role = 'ADMIN';

-- 4. Remove MEDIA_EVENT_WRITE direct grants (permission is being deleted from the enum).
delete from user_permissions where permission = 'MEDIA_EVENT_WRITE';


-- 5. Narrow CHECK constraints to remove the retired values.
--    Same discovery pattern as V6: drop whatever CHECK currently constrains the column,
--    then add a named replacement.

do $$
declare
    constraint_name text;
begin
    for constraint_name in
        select con.conname
        from pg_constraint con
        join pg_class rel on rel.oid = con.conrelid
        where rel.relname = 'user_roles'
          and con.contype = 'c'
    loop
        execute format('alter table user_roles drop constraint %I', constraint_name);
    end loop;

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

alter table user_roles
    add constraint user_roles_role_check check (role in (
        'USER',
        'BOARD_VIEWER','BOARD_EDITOR','BOARD_ADMIN',
        'TCKET_SCANNER','TCKET_MANAGER','TCKET_ADMIN',
        'ROOT'));

alter table user_permissions
    add constraint user_permissions_permission_check check (permission in (
        'MEDIA_UPLOAD_SELF','MEDIA_UPLOAD_MODERATE','MEDIA_UPLOAD_READ',
        'BOARD_CONTENT_READ','BOARD_POSTER_WRITE','BOARD_EVENT_WRITE','BOARD_SOCIAL_WRITE',
        'BOARD_WEEKLY_WRITE','BOARD_TICKER_WRITE',
        'BOARD_CONFIG_READ','BOARD_CONFIG_WRITE','BOARD_REFRESH',
        'BOARD_DEVICE_READ','BOARD_DEVICE_ENROLL','BOARD_DEVICE_REVOKE','BOARD_TELEMETRY_SUBSCRIBE',
        'BOARD_COMMAND_BENIGN','BOARD_COMMAND_DISRUPTIVE','BOARD_COMMAND_INSPECT',
        'TCKET_SCAN','TCKET_MANAGE','TCKET_ADMIN',
        'IAM_USER_READ','IAM_USER_WRITE','IAM_ROLE_GRANT','AUDIT_READ'));
