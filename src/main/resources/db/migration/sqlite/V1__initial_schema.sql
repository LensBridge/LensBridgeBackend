--
-- V1: the whole schema, as of the move from Hibernate ddl-auto to Flyway.
--
-- Generated from the JPA entity model rather than written by hand, so it is exactly what
-- `spring.jpa.hibernate.ddl-auto=validate` expects on startup. docs/MIGRATIONS.md has the
-- regeneration command -- though regenerating only makes sense while V1 is the only
-- migration. Once this has run anywhere, editing it fails Flyway's checksum: add a V2.
--
-- SQLite specifics:
--   * UUIDs are blob, timestamps are naive `timestamp` -- this is what the SQLite dialect
--     emits, and validate is checked against it.
--   * No foreign keys. SQLite cannot ALTER TABLE ADD CONSTRAINT, so Hibernate never emitted
--     them here either; prod (Postgres) does have all 12. Dev will therefore accept an
--     orphaned row that prod rejects.
--   * The two unique indexes at the bottom are added by hand. The SQLite dialect drops the
--     table-level unique constraints that Postgres gets, which would let dev accept a
--     duplicate enrollment-token hash or two rows for the same week -- both of which the
--     application assumes cannot exist.

create table audit_events (timestamp timestamp not null, admin_id blob not null, id blob not null, target_entity_id blob, details varchar(512), action varchar(255) not null check (action in ('APPROVE_UPLOAD','UNAPPROVE_UPLOAD','DELETE_UPLOAD','FEATURE_UPLOAD','UNFEATURE_UPLOAD','CREATE_EVENT','UPDATE_EVENT','DELETE_EVENT','CREATE_CALENDAR_EVENT','UPDATE_CALENDAR_EVENT','DELETE_CALENDAR_EVENT','CREATE_POSTER','UPDATE_POSTER','DELETE_POSTER','CREATE_SOCIAL','UPDATE_SOCIAL','DELETE_SOCIAL','SAVE_WEEKLY_CONTENT','DELETE_WEEKLY_CONTENT','UPDATE_BOARD_CONFIG','UPDATE_BOARD_TICKER','REFRESH_BOARDS','ISSUE_ENROLLMENT_TOKEN','REVOKE_DEVICE','ISSUE_DEVICE_COMMAND','PROMOTE_USER','DEMOTE_USER','DISABLE_USER','ENABLE_USER','VIEW_AUDIT_LOGS','EXPORT_DATA','SYSTEM_MAINTENANCE','VERIFY_USER','UNVERIFY_USER','RESET_USER_PASSWORD','TRIGGER_PASSWORD_RESET_EMAIL','ADD_USER_ROLE','REMOVE_USER_ROLE','GRANT_PERMISSION','REVOKE_PERMISSION','ADD_USER','REMOVE_USER','UPDATE_USER')), ip_address varchar(255), target_entity_type varchar(255) check (target_entity_type in ('USER','UPLOAD','MUSALLAH_BOARD','DEVICE','EVENT')), user_agent varchar(255), primary key (id));
create table board_config_messages (device_id blob not null, message varchar(255));
create table board_configs (agenda_duration_seconds integer, dark_mode_after_isha boolean not null, enable_scrolling_message boolean not null, latitude float, longitude float, next_prayer_duration_seconds integer, device_id blob not null, city varchar(255), country varchar(255), method varchar(255) check (method in ('KARACHI','ISNA','MWL','MAKKAH','EGYPT','TEHRAN','GULF','KUWAIT','QATAR','SINGAPORE','FRANCE','TURKEY','RUSSIA','DUBAI')), timezone varchar(255), primary key (device_id));
create table board_events (all_day boolean not null, end_time timestamp not null, start_time timestamp not null, id blob not null, audience varchar(255) not null check (audience in ('BROTHERS','SISTERS','BOTH')), description TEXT, location varchar(255), name varchar(255) not null, primary key (id));
create table device_commands (deadline_ms integer, acked_at timestamp, delivered_at timestamp, expires_at timestamp, finished_at timestamp, issued_at timestamp not null, started_at timestamp, id blob not null, device_id blob not null, error_message TEXT, issued_by varchar(255) not null, kind varchar(255) not null, output_json TEXT, payload_json TEXT, status varchar(255) not null check (status in ('PENDING','DELIVERED','ACKED','RUNNING','SUCCEEDED','FAILED','TIMEOUT','REJECTED','EXPIRED')), primary key (id));
create table device_telemetry (cpu_tempc float, disk_used_pct integer, kiosk_alive boolean, mem_total_mb integer, mem_used_mb integer, recorded_at timestamp not null, uptime_sec bigint, id blob not null, displayed_frame_key varchar(64), device_id blob not null, ipv4 varchar(255), throttle_flags varchar(255), wifi_ssid varchar(255), primary key (id));
create table devices (enrolled_at timestamp, last_heartbeat timestamp, revoked_at timestamp, id blob not null, agent_version varchar(255), audience varchar(255) not null check (audience in ('BROTHERS','SISTERS','BOTH')), display_name varchar(255) not null, hardware_model varchar(255), last_seen_ip varchar(255), organization_id varchar(255) not null, public_key blob, primary key (id));
create table enrollment_tokens (consumed_at timestamp, created_at timestamp not null, expires_at timestamp not null, consumed_by_device_id blob, id blob not null, audience varchar(255) not null check (audience in ('BROTHERS','SISTERS','BOTH')), created_by varchar(255) not null, display_name varchar(255) not null, token_hash blob not null, primary key (id));
create table events (date timestamp not null, id blob not null, name varchar(255) not null, status varchar(255) not null check (status in ('UPCOMING','ONGOING','PAST')), primary key (id));
create table islamic_quotes (id blob not null, weekly_content_id blob not null, arabic varchar(255), kind varchar(255) check (kind in ('VERSE','HADITH')), reference varchar(255), translation varchar(255), transliteration varchar(255), primary key (id));
create table jummah_prayers (prayer_time time(6), slot_order integer, id blob not null, weekly_content_id blob not null, khatib varchar(255), room varchar(255), primary key (id));
create table posters (duration integer not null, end_time timestamp not null, start_time timestamp not null, id blob not null, audience varchar(255) not null check (audience in ('BROTHERS','SISTERS','BOTH')), image varchar(255) not null, signup_url varchar(255), title varchar(255) not null, primary key (id));
create table promotable_social_media (duration integer not null, id blob not null, audience varchar(255) not null check (audience in ('BROTHERS','SISTERS','BOTH')), footer_text varchar(255) not null, handle varchar(255), header_text varchar(255) not null, hero_text varchar(255) not null, name varchar(255) not null, type varchar(255) not null check (type in ('INSTAGRAM','YOUTUBE','TIKTOK','WHATSAPP','OTHER')), url varchar(255) not null, primary key (id));
create table refresh_tokens (revoked boolean not null, created_date timestamp not null, expiry_date timestamp not null, last_used_date timestamp not null, id blob not null, user_id blob not null, token_hash varchar(255) not null unique, primary key (id));
create table uploads (approved boolean not null, featured boolean not null, is_anon boolean not null, created_date timestamp, deleted_at timestamp, deleted_by_id blob, event_id blob, uploaded_by blob not null, uuid blob not null, content_type varchar(255) check (content_type in ('IMAGE','VIDEO','AUDIO','DOCUMENT')), file_name varchar(255), file_url varchar(255), instagram_handle varchar(255), thumbnail_url varchar(255), upload_description varchar(255), primary key (uuid));
create table user_permissions (user_id blob not null, permission varchar(255) not null check (permission in ('MEDIA_UPLOAD_SELF','MEDIA_UPLOAD_MODERATE','MEDIA_UPLOAD_READ','MEDIA_EVENT_WRITE','BOARD_CONTENT_READ','BOARD_POSTER_WRITE','BOARD_EVENT_WRITE','BOARD_SOCIAL_WRITE','BOARD_WEEKLY_WRITE','BOARD_TICKER_WRITE','BOARD_CONFIG_READ','BOARD_CONFIG_WRITE','BOARD_REFRESH','BOARD_DEVICE_READ','BOARD_DEVICE_ENROLL','BOARD_DEVICE_REVOKE','BOARD_TELEMETRY_SUBSCRIBE','BOARD_COMMAND_BENIGN','BOARD_COMMAND_DISRUPTIVE','BOARD_COMMAND_INSPECT','IAM_USER_READ','IAM_USER_WRITE','IAM_ROLE_GRANT','AUDIT_READ')), primary key (user_id, permission));
create table user_roles (user_id blob not null, role varchar(255) not null check (role in ('USER','ADMIN','BOARD_VIEWER','BOARD_EDITOR','BOARD_ADMIN','ROOT')), primary key (user_id, role));
create table users (verified_at timestamp, student_number varchar(10) not null unique, id blob not null, email varchar(254) not null unique, first_name varchar(255) not null, last_name varchar(255) not null, password_hash varchar(255) not null, primary key (id));
create table verification_tokens (created_at timestamp, expires_at timestamp, used_at timestamp, id blob not null, user_id blob not null, token_hash varchar(255) not null unique, type varchar(255) not null check (type in ('EMAIL_VERIFICATION','PASSWORD_RESET')), primary key (id));
create table weekly_content (week_number integer not null, year integer not null, id blob not null, primary key (id));
create index idx_device_commands_device_status on device_commands (device_id, status);
create index idx_device_commands_pending on device_commands (device_id, issued_at);
create index idx_device_telemetry_device_recorded on device_telemetry (device_id, recorded_at);
create index idx_users_email on users (email);

-- Uniqueness Postgres enforces via table constraints; see the note above.
create unique index idx_enrollment_tokens_hash on enrollment_tokens (token_hash);
create unique index idx_weekly_content_year_week on weekly_content (year, week_number);
