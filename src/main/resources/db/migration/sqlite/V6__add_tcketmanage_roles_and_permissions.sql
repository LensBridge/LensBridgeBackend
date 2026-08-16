--
-- V6: widen the user_roles and user_permissions CHECK constraints for the ticketing
-- roles and permissions (TCKET_SCANNER/MANAGER/ADMIN, tcket:scan/manage/admin).
--
-- Adding the enum constants alone is not enough. Both columns are persisted with
-- @Enumerated(STRING) and carry a CHECK listing every legal value, so a user granted a
-- TCKET_* role would be rejected by the database on INSERT. Hibernate's ddl-auto=validate
-- does not inspect CHECK constraints, so this would have passed startup validation and
-- only failed the first time someone actually granted the role.
--
-- SQLite cannot ALTER a CHECK constraint, so each table is rebuilt: create alongside,
-- copy, drop, rename (see docs/MIGRATIONS.md, "Known dev/prod divergences"). Neither
-- table declares a foreign key in SQLite -- the postgres twin adds those via ALTER, which
-- is why only that side has to re-add them -- so the rebuild is a straight copy.
--
-- The postgres twin of this file must stay at the same version -- see docs/MIGRATIONS.md.

create table user_roles_new (
    user_id blob         not null,
    role    varchar(255) not null check (role in (
        'USER','ADMIN',
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
        'MEDIA_UPLOAD_SELF','MEDIA_UPLOAD_MODERATE','MEDIA_UPLOAD_READ','MEDIA_EVENT_WRITE',
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
