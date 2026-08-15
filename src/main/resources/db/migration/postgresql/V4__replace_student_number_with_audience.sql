--
-- V4: replace users.student_number with users.audience.
--
-- Existing rows have no audience opinion recorded, so they backfill to BROTHERS -- the same
-- default the User(...) constructor uses for newly created accounts. The default is dropped
-- once every row has a value, so the column ends up matching every other audience column in
-- this schema: required, with no default of its own.
--
-- The SQLite twin of this file must stay at the same version -- see docs/MIGRATIONS.md.

alter table users add column audience varchar(255) not null default 'BROTHERS'
    check (audience in ('BROTHERS','SISTERS','BOTH'));
alter table users alter column audience drop default;
alter table users drop column student_number;
