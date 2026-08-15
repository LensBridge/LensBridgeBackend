create table prayer_spaces (
    capacity integer,
    walk_time_minutes integer,
    id varchar(36) not null,
    audience varchar(255) not null check (audience in ('BROTHERS','SISTERS','BOTH')),
    building varchar(255) not null,
    entrance_description varchar(255),
    entrance_name varchar(255),
    floor varchar(255),
    location varchar(255),
    maps_url varchar(255),
    name varchar(255) not null,
    starting_point varchar(255),
    tag varchar(255) not null,
    primary key (id)
);

create table prayer_space_amenities (
    sort_order integer not null,
    prayer_space_id varchar(36) not null,
    amenity varchar(255) not null
);

create table prayer_space_steps (
    step_order integer not null,
    id varchar(36) not null,
    prayer_space_id varchar(36) not null,
    instruction varchar(255) not null,
    subtext varchar(255),
    primary key (id)
);

create table prayer_space_tips (
    sort_order integer not null,
    prayer_space_id varchar(36) not null,
    tip varchar(255) not null
);
