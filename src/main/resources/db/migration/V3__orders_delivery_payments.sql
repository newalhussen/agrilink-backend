-- =====================================================================
-- V3: orders, deliveries, payments, wallets
-- =====================================================================

create sequence order_number_seq start with 20001 increment by 1;

create table orders (
    id                          uuid primary key,
    order_number                varchar(20)   not null unique,
    buyer_id                    uuid          not null references users (id),
    farmer_id                   uuid          not null references users (id),
    driver_id                   uuid references users (id),
    status                      varchar(20)   not null,
    subtotal_amount             numeric(14,2) not null,
    delivery_fee                numeric(14,2) not null,
    platform_fee                numeric(14,2) not null,
    total_amount                numeric(14,2) not null,
    currency                    varchar(3)    not null default 'ETB',
    total_weight_kg             numeric(14,3) not null,
    delivery_region_id          uuid references regions (id),
    delivery_zone               varchar(100),
    delivery_woreda             varchar(100),
    delivery_town               varchar(100),
    delivery_address_line       varchar(255),
    delivery_latitude           double precision,
    delivery_longitude          double precision,
    delivery_contact_name       varchar(150),
    delivery_contact_phone      varchar(20),
    requested_delivery_date     date,
    buyer_notes                 varchar(1000),
    farmer_response_deadline    timestamptz,
    payment_deadline            timestamptz,
    check_window_ends_at        timestamptz,
    status_reason               varchar(500),
    status_before_dispute       varchar(20),
    accepted_at                 timestamptz,
    paid_at                     timestamptz,
    ready_at                    timestamptz,
    picked_up_at                timestamptz,
    delivered_at                timestamptz,
    completed_at                timestamptz,
    cancelled_at                timestamptz,
    created_at                  timestamptz   not null default now(),
    updated_at                  timestamptz   not null default now(),
    created_by                  uuid,
    updated_by                  uuid,
    version                     bigint        not null default 0
);
create index ix_orders_buyer on orders (buyer_id, created_at desc);
create index ix_orders_farmer on orders (farmer_id, created_at desc);
create index ix_orders_driver on orders (driver_id);
create index ix_orders_status on orders (status);
create index ix_orders_pending_deadline on orders (farmer_response_deadline) where status = 'PENDING';
create index ix_orders_payment_deadline on orders (payment_deadline) where status in ('ACCEPTED', 'PAYMENT_PENDING');
create index ix_orders_check_window on orders (check_window_ends_at) where status = 'DELIVERED';

create table order_items (
    id              uuid primary key,
    order_id        uuid          not null references orders (id) on delete cascade,
    listing_id      uuid          not null references listings (id),
    product_id      uuid          not null references products (id),
    product_name    varchar(150)  not null,
    quality_grade   varchar(5),
    unit            varchar(20)   not null,
    quantity        numeric(14,3) not null,
    unit_price      numeric(14,2) not null,
    unit_weight_kg  numeric(10,3) not null,
    line_total      numeric(14,2) not null,
    created_at      timestamptz   not null default now(),
    updated_at      timestamptz   not null default now(),
    created_by      uuid,
    updated_by      uuid,
    version         bigint        not null default 0
);
create index ix_order_items_order on order_items (order_id);
create index ix_order_items_listing on order_items (listing_id);

create table order_status_history (
    id          uuid primary key,
    order_id    uuid        not null references orders (id) on delete cascade,
    from_status varchar(20),
    to_status   varchar(20) not null,
    actor_id    uuid,
    actor_role  varchar(20),
    note        varchar(500),
    created_at  timestamptz not null default now()
);
create index ix_order_history_order on order_status_history (order_id, created_at);

create table deliveries (
    id                       uuid primary key,
    order_id                 uuid          not null unique references orders (id),
    driver_id                uuid references users (id),
    status                   varchar(20)   not null,
    pickup_region_id         uuid references regions (id),
    pickup_zone              varchar(100),
    pickup_woreda            varchar(100),
    pickup_town              varchar(100),
    pickup_address_line      varchar(255),
    pickup_latitude          double precision,
    pickup_longitude         double precision,
    pickup_contact_name      varchar(150),
    pickup_contact_phone     varchar(20),
    dropoff_region_id        uuid references regions (id),
    dropoff_zone             varchar(100),
    dropoff_woreda           varchar(100),
    dropoff_town             varchar(100),
    dropoff_address_line     varchar(255),
    dropoff_latitude         double precision,
    dropoff_longitude        double precision,
    dropoff_contact_name     varchar(150),
    dropoff_contact_phone    varchar(20),
    distance_km              numeric(10,2),
    total_weight_kg          numeric(14,3) not null,
    driver_fee               numeric(14,2) not null,
    scheduled_pickup_date    date,
    pickup_code              varchar(10)   not null,
    pickup_qr_token          varchar(64)   not null unique,
    delivery_code            varchar(10)   not null,
    delivery_qr_token        varchar(64)   not null unique,
    pickup_failed_attempts   integer       not null default 0,
    delivery_failed_attempts integer       not null default 0,
    assigned_at              timestamptz,
    picked_up_at             timestamptz,
    delivered_at             timestamptz,
    picked_up_weight_kg      numeric(14,3),
    picked_up_crate_count    integer,
    pickup_note              varchar(500),
    delivery_note            varchar(500),
    cancelled_reason         varchar(500),
    created_at               timestamptz   not null default now(),
    updated_at               timestamptz   not null default now(),
    created_by               uuid,
    updated_by               uuid,
    version                  bigint        not null default 0
);
create index ix_deliveries_status on deliveries (status);
create index ix_deliveries_driver on deliveries (driver_id, status);

create table delivery_events (
    id          uuid primary key,
    delivery_id uuid             not null references deliveries (id) on delete cascade,
    event_type  varchar(30)      not null,
    latitude    double precision,
    longitude   double precision,
    note        varchar(500),
    actor_id    uuid,
    created_at  timestamptz      not null default now()
);
create index ix_delivery_events_delivery on delivery_events (delivery_id, created_at);

create table delivery_attachments (
    id          uuid primary key,
    delivery_id uuid        not null references deliveries (id) on delete cascade,
    stage       varchar(20) not null,
    file_id     uuid        not null references file_assets (id),
    uploaded_by uuid        not null references users (id),
    created_at  timestamptz not null default now()
);
create index ix_delivery_attachments_delivery on delivery_attachments (delivery_id);

create table payments (
    id                      uuid primary key,
    order_id                uuid          not null references orders (id),
    payer_id                uuid          not null references users (id),
    status                  varchar(24)   not null,
    method                  varchar(20)   not null,
    provider                varchar(30)   not null,
    transaction_reference   varchar(60)   not null unique,
    provider_reference      varchar(100),
    amount                  numeric(14,2) not null,
    goods_amount            numeric(14,2) not null,
    delivery_amount         numeric(14,2) not null,
    platform_fee_amount     numeric(14,2) not null,
    currency                varchar(3)    not null default 'ETB',
    payer_account           varchar(40),
    checkout_url            varchar(500),
    failure_reason          varchar(500),
    initiated_at            timestamptz   not null,
    paid_at                 timestamptz,
    expires_at              timestamptz,
    released_at             timestamptz,
    refunded_amount         numeric(14,2) not null default 0,
    released_farmer_amount  numeric(14,2) not null default 0,
    released_driver_amount  numeric(14,2) not null default 0,
    platform_fee_retained   numeric(14,2) not null default 0,
    created_at              timestamptz   not null default now(),
    updated_at              timestamptz   not null default now(),
    created_by              uuid,
    updated_by              uuid,
    version                 bigint        not null default 0
);
create index ix_payments_order on payments (order_id);
create index ix_payments_payer on payments (payer_id, created_at desc);
create index ix_payments_status on payments (status);
create unique index ux_payments_one_active_per_order on payments (order_id) where status in ('PENDING', 'HELD');

create table wallets (
    id          uuid primary key,
    user_id     uuid          not null unique references users (id),
    balance     numeric(14,2) not null default 0,
    currency    varchar(3)    not null default 'ETB',
    created_at  timestamptz   not null default now(),
    updated_at  timestamptz   not null default now(),
    created_by  uuid,
    updated_by  uuid,
    version     bigint        not null default 0,
    constraint ck_wallet_balance check (balance >= 0)
);

create table payouts (
    id                  uuid primary key,
    wallet_id           uuid          not null references wallets (id),
    user_id             uuid          not null references users (id),
    amount              numeric(14,2) not null,
    method              varchar(20)   not null,
    destination_account varchar(40)   not null,
    destination_name    varchar(150),
    status              varchar(20)   not null,
    provider            varchar(30)   not null,
    provider_reference  varchar(100),
    failure_reason      varchar(500),
    processed_at        timestamptz,
    created_at          timestamptz   not null default now(),
    updated_at          timestamptz   not null default now(),
    created_by          uuid,
    updated_by          uuid,
    version             bigint        not null default 0,
    constraint ck_payout_amount check (amount > 0)
);
create index ix_payouts_user on payouts (user_id, created_at desc);
create index ix_payouts_status on payouts (status);

create table wallet_transactions (
    id             uuid primary key,
    wallet_id      uuid          not null references wallets (id),
    type           varchar(30)   not null,
    direction      varchar(10)   not null,
    amount         numeric(14,2) not null,
    balance_after  numeric(14,2) not null,
    order_id       uuid references orders (id),
    payment_id     uuid references payments (id),
    payout_id      uuid references payouts (id),
    reference      varchar(80),
    description    varchar(255),
    created_at     timestamptz   not null default now(),
    updated_at     timestamptz   not null default now(),
    created_by     uuid,
    updated_by     uuid,
    version        bigint        not null default 0
);
create index ix_wallet_tx_wallet on wallet_transactions (wallet_id, created_at desc);
