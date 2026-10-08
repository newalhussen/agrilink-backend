-- =====================================================================
-- V2: marketplace listings
-- =====================================================================

create table listings (
    id                  uuid primary key,
    farmer_id           uuid          not null references users (id),
    product_id          uuid          not null references products (id),
    title               varchar(150)  not null,
    description         varchar(2000),
    quality_grade       varchar(5)    not null default 'A',
    quality_notes       varchar(1000),
    packaging           varchar(100),
    harvest_date        date,
    available_from      date          not null,
    available_until     date,
    unit                varchar(20)   not null,
    unit_weight_kg      numeric(10,3) not null,
    quantity_total      numeric(14,3) not null,
    quantity_available  numeric(14,3) not null,
    min_order_quantity  numeric(14,3) not null default 1,
    price_per_unit      numeric(14,2) not null,
    currency            varchar(3)    not null default 'ETB',
    organic             boolean       not null default false,
    region_id           uuid references regions (id),
    zone                varchar(100),
    woreda              varchar(100),
    town                varchar(100),
    address_line        varchar(255),
    latitude            double precision,
    longitude           double precision,
    status              varchar(20)   not null default 'ACTIVE',
    published_at        timestamptz,
    created_at          timestamptz   not null default now(),
    updated_at          timestamptz   not null default now(),
    created_by          uuid,
    updated_by          uuid,
    version             bigint        not null default 0,
    constraint ck_listings_qty check (quantity_available >= 0 and quantity_total >= 0 and quantity_available <= quantity_total),
    constraint ck_listings_price check (price_per_unit > 0),
    constraint ck_listings_min check (min_order_quantity > 0)
);
create index ix_listings_status_product on listings (status, product_id);
create index ix_listings_farmer on listings (farmer_id, status);
create index ix_listings_region on listings (region_id);
create index ix_listings_price on listings (price_per_unit);
create index ix_listings_available_from on listings (available_from);

create table listing_photos (
    id          uuid primary key,
    listing_id  uuid        not null references listings (id) on delete cascade,
    file_id     uuid        not null references file_assets (id),
    sort_order  integer     not null default 0,
    is_primary  boolean     not null default false,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    created_by  uuid,
    updated_by  uuid,
    version     bigint      not null default 0
);
create index ix_listing_photos_listing on listing_photos (listing_id);
