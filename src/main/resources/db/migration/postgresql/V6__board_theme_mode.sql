--
-- V6: a board's light/dark appearance becomes a three-way choice.
--
--   board_configs.theme_mode   LIGHT, DARK or AUTO. AUTO is dark from Maghrib until sunrise
--                              (the board computes both times itself), light otherwise.
--
-- It replaces dark_mode_after_isha, an on/off for "dark after Isha". Existing boards keep what
-- they chose: on becomes AUTO (the dark window now opens at Maghrib rather than Isha), off
-- becomes LIGHT. The old column is dropped, not left behind: the payload no longer carries it,
-- and a column nothing reads is a column someone later trusts.
--
-- The SQLite twin of this file must stay at the same version -- see MigrationParityTest.

alter table board_configs
    add column theme_mode varchar(255) not null default 'AUTO'
    check (theme_mode in ('LIGHT','DARK','AUTO'));

update board_configs
   set theme_mode = case when dark_mode_after_isha then 'AUTO' else 'LIGHT' end;

alter table board_configs drop column dark_mode_after_isha;
