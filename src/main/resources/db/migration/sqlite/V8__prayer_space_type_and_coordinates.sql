--
-- V8: give a prayer space a type, a pin on the map, and somewhere to put a photo,
--     and admit BOARD_PRAYER_SPACE_WRITE into the permission CHECK.
--
-- See the Postgres twin for why space_type exists at all and why `location` becomes
-- `room_info`. The difference here is mechanical: SQLite cannot add a NOT NULL column to a
-- populated table without a DEFAULT, cannot promote a column to NOT NULL afterwards, and
-- cannot alter a CHECK constraint -- so both tables are rebuilt, the same pattern V4, V6
-- and V7 use.
--
-- Dropping prayer_spaces is safe here even though three child tables reference it: the
-- SQLite twin of V3 declares them without FK constraints (SQLite does not enforce foreign
-- keys by default), so their rows survive the swap and keep pointing at the same ids.
--
-- The Postgres twin of this file must stay at the same version -- see docs/MIGRATIONS.md.


-- 1. Rebuild prayer_spaces: rename location -> room_info, add space_type (backfilled from
--    audience), coordinates, image, notes, and prose directions.

create table prayer_spaces_new (
    capacity integer,
    walk_time_minutes integer,
    latitude float,
    longitude float,
    id blob not null,
    audience varchar(255) not null check (audience in ('BROTHERS','SISTERS','BOTH')),
    space_type varchar(255) not null check (space_type in ('BROTHERS','SISTERS','MULTIFAITH','REFLECTION')),
    building varchar(255) not null,
    entrance_description varchar(255),
    entrance_name varchar(255),
    floor varchar(255),
    room_info varchar(255),
    image_url varchar(255),
    maps_url varchar(255),
    name varchar(255) not null,
    starting_point varchar(255),
    tag varchar(255) not null,
    notes TEXT,
    directions TEXT,
    primary key (id)
);

insert into prayer_spaces_new (
    capacity, walk_time_minutes, latitude, longitude, id, audience, space_type, building,
    entrance_description, entrance_name, floor, room_info, image_url, maps_url, name,
    starting_point, tag, notes, directions)
select
    capacity, walk_time_minutes, null, null, id, audience,
    case audience
        when 'BROTHERS' then 'BROTHERS'
        when 'SISTERS' then 'SISTERS'
        else 'MULTIFAITH'
    end,
    building, entrance_description, entrance_name, floor, location, null, maps_url, name,
    starting_point, tag, null, null
from prayer_spaces;

drop table prayer_spaces;

alter table prayer_spaces_new rename to prayer_spaces;


-- 2. Rebuild user_permissions with BOARD_PRAYER_SPACE_WRITE in the CHECK.

create table user_permissions_new (
    user_id    blob         not null,
    permission varchar(255) not null check (permission in (
        'MEDIA_UPLOAD_SELF','MEDIA_UPLOAD_MODERATE','MEDIA_UPLOAD_READ',
        'BOARD_CONTENT_READ','BOARD_POSTER_WRITE','BOARD_EVENT_WRITE','BOARD_SOCIAL_WRITE',
        'BOARD_WEEKLY_WRITE','BOARD_TICKER_WRITE','BOARD_PRAYER_SPACE_WRITE',
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
