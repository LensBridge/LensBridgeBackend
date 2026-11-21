--
-- V4: room for real-world board text.
--
-- V1 gave every string column Hibernate's default varchar(255). That is fine for names and
-- titles but not for the free text an editor pastes in: a normal-length hadith translation
-- or a long ticker message overflowed it, which Postgres rejects, so the save came back as a
-- 500. These columns now match the @Column(length) on the entities and the @Size(max) on the
-- request DTOs, so an over-long value is a 400 before it ever reaches the database.
--
--   quote text and ticker messages  4000
--   quote reference, social texts   1000
--
-- Names, titles, urls and handles stay at 255 (and are capped at 255 by the DTOs).
-- Widening a varchar is a metadata-only change in Postgres: no table rewrite, no data loss.
--
-- The SQLite twin of this file is a no-op, since SQLite does not enforce varchar lengths --
-- see docs/MIGRATIONS.md.

alter table islamic_quotes
    alter column arabic type varchar(4000),
    alter column transliteration type varchar(4000),
    alter column translation type varchar(4000),
    alter column reference type varchar(1000);

alter table promotable_social_media
    alter column header_text type varchar(1000),
    alter column hero_text type varchar(1000),
    alter column footer_text type varchar(1000);

alter table board_config_messages
    alter column message type varchar(4000);
