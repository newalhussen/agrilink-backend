-- =====================================================================
-- V1: reference regions, identity, authentication, role profiles, files
-- Every table carries the same audit columns (created_at/updated_at/
-- created_by/updated_by/version) populated by Spring Data JPA auditing.
-- =====================================================================

create table regions (
    id          uuid primary key,
    code        varchar(20)  not null unique,
    name_en     varchar(100) not null,
    name_am     varchar(100),
    name_om     varchar(100),
    active      boolean      not null default true,
    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now(),
    created_by  uuid,
    updated_by  uuid,
    version     bigint       not null default 0
);

create table users (
    id                      uuid primary key,
    phone                   varchar(20)  not null unique,
    email                   varchar(255),
    password_hash           varchar(100),
    full_name               varchar(150) not null,
    role                    varchar(20)  not null,
    account_status          varchar(20)  not null default 'ACTIVE',
    verification_status     varchar(20)  not null default 'UNVERIFIED',
    preferred_language      varchar(5)   not null default 'en',
    phone_verified          boolean      not null default false,
    phone_verified_at       timestamptz,
    profile_photo_file_id   uuid,
    rating_average          numeric(3,2) not null default 0,
    rating_count            integer      not null default 0,
    failed_login_attempts   integer      not null default 0,
    locked_until            timestamptz,
    last_login_at           timestamptz,
    status_reason           varchar(500),
    created_at              timestamptz  not null default now(),
    updated_at              timestamptz  not null default now(),
    created_by              uuid,
    updated_by              uuid,
    version                 bigint       not null default 0,
    constraint ck_users_role check (role in ('FARMER', 'BUYER', 'DRIVER', 'ADMIN'))
);
create unique index ux_users_email on users (lower(email)) where email is not null;
create index ix_users_role_status on users (role, account_status);
create index ix_users_verification on users (verification_status, role);

create table file_assets (
    id                uuid primary key,
    owner_id          uuid         not null references users (id),
    purpose           varchar(40)  not null,
    visibility        varchar(10)  not null,
    original_filename varchar(255) not null,
    content_type      varchar(100) not null,
    size_bytes        bigint       not null,
    storage_key       varchar(300) not null unique,
    checksum_sha256   varchar(64),
    created_at        timestamptz  not null default now(),
    updated_at        timestamptz  not null default now(),
    created_by        uuid,
    updated_by        uuid,
    version           bigint       not null default 0
);
create index ix_file_assets_owner on file_assets (owner_id);

alter table users
    add constraint fk_users_profile_photo foreign key (profile_photo_file_id) references file_assets (id);

create table otp_codes (
    id          uuid primary key,
    phone       varchar(20)  not null,
    purpose     varchar(30)  not null,
    code_hash   varchar(100) not null,
    expires_at  timestamptz  not null,
    attempts    integer      not null default 0,
    consumed_at timestamptz,
    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now(),
    created_by  uuid,
    updated_by  uuid,
    version     bigint       not null default 0
);
create index ix_otp_phone_purpose on otp_codes (phone, purpose, created_at desc);

create table refresh_tokens (
    id           uuid primary key,
    user_id      uuid         not null references users (id) on delete cascade,
    family_id    uuid         not null,
    token_hash   varchar(64)  not null unique,
    expires_at   timestamptz  not null,
    revoked_at   timestamptz,
    user_agent   varchar(300),
    ip_address   varchar(60),
    created_at   timestamptz  not null default now(),
    updated_at   timestamptz  not null default now(),
    created_by   uuid,
    updated_by   uuid,
    version      bigint       not null default 0
);
create index ix_refresh_user on refresh_tokens (user_id);
create index ix_refresh_family on refresh_tokens (family_id);

-- ---------------------------------------------------------------------
-- Role profiles
-- ---------------------------------------------------------------------
create table product_categories (
    id          uuid primary key,
    slug        varchar(60)  not null unique,
    name_en     varchar(100) not null,
    name_am     varchar(100),
    name_om     varchar(100),
    icon        varchar(60),
    sort_order  integer      not null default 0,
    active      boolean      not null default true,
    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now(),
    created_by  uuid,
    updated_by  uuid,
    version     bigint       not null default 0
);

create table products (
    id           uuid primary key,
    category_id  uuid         not null references product_categories (id),
    slug         varchar(80)  not null unique,
    name_en      varchar(100) not null,
    name_am      varchar(100),
    name_om      varchar(100),
    default_unit varchar(20)  not null,
    description  varchar(1000),
    active       boolean      not null default true,
    created_at   timestamptz  not null default now(),
    updated_at   timestamptz  not null default now(),
    created_by   uuid,
    updated_by   uuid,
    version      bigint       not null default 0
);
create index ix_products_category on products (category_id);

create table farmer_profiles (
    id                          uuid primary key,
    user_id                     uuid         not null unique references users (id) on delete cascade,
    farmer_type                 varchar(20)  not null default 'INDIVIDUAL',
    farm_name                   varchar(150),
    member_count                integer,
    region_id                   uuid references regions (id),
    zone                        varchar(100),
    woreda                      varchar(100),
    town                        varchar(100),
    address_line                varchar(255),
    latitude                    double precision,
    longitude                   double precision,
    land_size_hectares          numeric(10,2),
    irrigated                   boolean      not null default false,
    expected_monthly_supply_kg  numeric(14,2),
    bio                         varchar(1000),
    fayda_id_number             varchar(30),
    payout_method               varchar(20),
    payout_account_name         varchar(150),
    payout_account_number       varchar(40),
    completed_trades            integer      not null default 0,
    created_at                  timestamptz  not null default now(),
    updated_at                  timestamptz  not null default now(),
    created_by                  uuid,
    updated_by                  uuid,
    version                     bigint       not null default 0
);
create index ix_farmer_profiles_region on farmer_profiles (region_id);

create table farmer_profile_products (
    farmer_profile_id uuid not null references farmer_profiles (id) on delete cascade,
    product_id        uuid not null references products (id),
    primary key (farmer_profile_id, product_id)
);

create table buyer_profiles (
    id                      uuid primary key,
    user_id                 uuid         not null unique references users (id) on delete cascade,
    buyer_type              varchar(30)  not null default 'INDIVIDUAL',
    business_name           varchar(150),
    contact_person          varchar(150),
    tin_number              varchar(30),
    trade_license_number    varchar(60),
    region_id               uuid references regions (id),
    zone                    varchar(100),
    woreda                  varchar(100),
    town                    varchar(100),
    address_line            varchar(255),
    latitude                double precision,
    longitude               double precision,
    delivery_instructions   varchar(500),
    completed_orders        integer      not null default 0,
    created_at              timestamptz  not null default now(),
    updated_at              timestamptz  not null default now(),
    created_by              uuid,
    updated_by              uuid,
    version                 bigint       not null default 0
);

create table driver_profiles (
    id                      uuid primary key,
    user_id                 uuid         not null unique references users (id) on delete cascade,
    license_number          varchar(60),
    license_expiry_date     date,
    vehicle_type            varchar(30),
    vehicle_plate           varchar(30),
    vehicle_make_model      varchar(100),
    vehicle_year            integer,
    capacity_kg             numeric(10,2),
    refrigerated            boolean      not null default false,
    region_id               uuid references regions (id),
    availability            varchar(20)  not null default 'OFFLINE',
    current_latitude        double precision,
    current_longitude       double precision,
    location_updated_at     timestamptz,
    payout_method           varchar(20),
    payout_account_name     varchar(150),
    payout_account_number   varchar(40),
    completed_deliveries    integer      not null default 0,
    created_at              timestamptz  not null default now(),
    updated_at              timestamptz  not null default now(),
    created_by              uuid,
    updated_by              uuid,
    version                 bigint       not null default 0
);
create unique index ux_driver_plate on driver_profiles (upper(vehicle_plate)) where vehicle_plate is not null;
create index ix_driver_availability on driver_profiles (availability);

-- ---------------------------------------------------------------------
-- Verification
-- ---------------------------------------------------------------------
create table verification_documents (
    id           uuid primary key,
    user_id      uuid         not null references users (id) on delete cascade,
    doc_type     varchar(40)  not null,
    file_id      uuid         not null references file_assets (id),
    status       varchar(20)  not null default 'PENDING',
    note         varchar(500),
    reviewed_by  uuid,
    reviewed_at  timestamptz,
    created_at   timestamptz  not null default now(),
    updated_at   timestamptz  not null default now(),
    created_by   uuid,
    updated_by   uuid,
    version      bigint       not null default 0
);
create index ix_verification_docs_user on verification_documents (user_id);

create table verification_reviews (
    id              uuid primary key,
    user_id         uuid         not null references users (id) on delete cascade,
    reviewer_id     uuid         not null references users (id),
    decision        varchar(20)  not null,
    previous_status varchar(20)  not null,
    new_status      varchar(20)  not null,
    note            varchar(1000),
    created_at      timestamptz  not null default now(),
    updated_at      timestamptz  not null default now(),
    created_by      uuid,
    updated_by      uuid,
    version         bigint       not null default 0
);
create index ix_verification_reviews_user on verification_reviews (user_id);
