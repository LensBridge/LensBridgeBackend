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
-- The postgres twin of this file must stay at the same version — see docs/MIGRATIONS.md.


-- 1. Add allow_uploads to board_events.
--    SQLite doesn't support ALTER COLUMN ... DEFAULT, but the NOT NULL + DEFAULT on ADD
--    COLUMN backfills existing rows.
alter table board_events add column allow_uploads boolean not null default 0;


-- 2. Repoint uploads.event_id from the "events" table to "board_events".
--    SQLite has no FK enforcement by default and no DROP CONSTRAINT, so we just null out
--    the orphaned references and drop the table.
update uploads set event_id = null;
drop table events;


-- 3. Remove the ADMIN role from anyone holding it.
delete from user_roles where role = 'ADMIN';

-- 4. Remove MEDIA_EVENT_WRITE direct grants.
delete from user_permissions where permission = 'MEDIA_EVENT_WRITE';


-- 5. Rebuild user_roles and user_permissions with narrowed CHECK constraints.
--    Same table-rebuild pattern as V6.

create table user_roles_new (
    user_id blob         not null,
    role    varchar(255) not null check (role in (
        'USER',
        'BOARD_VIEWER','BOARD_EDITOR','BOARD_ADMIN',
        'TCKET_SCANNER','TCKET_MANAGER','TCKET_ADMIN',
        'ROOT')),
    primary key (user_id, role)
);

insert into user_roles_new (user_id, role) select user_id, role from user_roles;
drop table user_roles;
alter table user_roles_new rename to user_roles;

create table user_permissions_new (
    user_id    blob         not null,
    permission varchar(255) not null check (permission in (
        'MEDIA_UPLOAD_SELF','MEDIA_UPLOAD_MODERATE','MEDIA_UPLOAD_READ',
        'BOARD_CONTENT_READ','BOARD_POSTER_WRITE','BOARD_EVENT_WRITE','BOARD_SOCIAL_WRITE',
        'BOARD_WEEKLY_WRITE','BOARD_TICKER_WRITE',
        'BOARD_CONFIG_READ','BOARD_CONFIG_WRITE','BOARD_REFRESH',
        'BOARD_DEVICE_READ','BOARD_DEVICE_ENROLL','BOARD_DEVICE_REVOKE','BOARD_TELEMETRY_SUBSCRIBE',
        'BOARD_COMMAND_BENIGN','BOARD_COMMAND_DISRUPTIVE','BOARD_COMMAND_INSPECT',
        'TCKET_SCAN','TCKET_MANAGE','TCKET_ADMIN',
        'IAM_USER_READ','IAM_USER_WRITE','IAM_ROLE_GRANT','AUDIT_READ')),
    primary key (user_id, permission)
);

insert into user_permissions_new (user_id, permission)
    select user_id, permission from user_permissions;
drop table user_permissions;
alter table user_permissions_new rename to user_permissions;
