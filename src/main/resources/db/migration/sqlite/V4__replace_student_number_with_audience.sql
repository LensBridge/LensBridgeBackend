--
-- V4: replace users.student_number with users.audience.
--
-- SQLite refuses to DROP COLUMN a column carrying a UNIQUE constraint (student_number was
-- `not null unique`), so this rebuilds the table rather than altering it in place. Existing
-- rows have no audience opinion recorded and backfill to BROTHERS, the same default the
-- User(...) constructor uses for newly created accounts. Unlike the postgres twin, the
-- DEFAULT stays on the column afterward -- SQLite's ALTER TABLE has no DROP DEFAULT, and
-- dropping it would mean another full rebuild for no functional gain in a dev-only database.
--
-- Dropping the table also drops idx_users_email (SQLite deletes a table's indexes along with
-- it), so that index is recreated at the end.
--
-- The postgres twin of this file must stay at the same version -- see docs/MIGRATIONS.md.

create table users_new (
    verified_at timestamp,
    audience varchar(255) not null default 'BROTHERS' check (audience in ('BROTHERS','SISTERS','BOTH')),
    id blob not null,
    email varchar(254) not null unique,
    first_name varchar(255) not null,
    last_name varchar(255) not null,
    password_hash varchar(255) not null,
    primary key (id)
);

insert into users_new (verified_at, audience, id, email, first_name, last_name, password_hash)
select verified_at, 'BROTHERS', id, email, first_name, last_name, password_hash
from users;

drop table users;

alter table users_new rename to users;

create index idx_users_email on users (email);
