-- =====================================================================
-- V4: ratings, disputes, notifications, admin audit trail
-- =====================================================================

create table ratings (
    id          uuid primary key,
    order_id    uuid         not null references orders (id),
    rater_id    uuid         not null references users (id),
    ratee_id    uuid references users (id),
    target      varchar(20)  not null,
    score       integer      not null,
    comment     varchar(1000),
    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now(),
    created_by  uuid,
    updated_by  uuid,
    version     bigint       not null default 0,
    constraint ck_rating_score check (score between 1 and 5),
    constraint ux_rating_once unique (order_id, rater_id, target)
);
create index ix_ratings_ratee on ratings (ratee_id, created_at desc);

create sequence dispute_number_seq start with 1 increment by 1;

create table disputes (
    id                          uuid primary key,
    dispute_number              varchar(20)   not null unique,
    order_id                    uuid          not null references orders (id),
    delivery_id                 uuid references deliveries (id),
    raised_by_id                uuid          not null references users (id),
    against_user_id             uuid references users (id),
    type                        varchar(30)   not null,
    description                 varchar(2000),
    claimed_received_quantity   numeric(14,3),
    status                      varchar(20)   not null,
    due_at                      timestamptz   not null,
    assigned_admin_id           uuid references users (id),
    resolution_type             varchar(20),
    farmer_amount               numeric(14,2),
    driver_amount               numeric(14,2),
    buyer_refund_amount         numeric(14,2),
    platform_retained_amount    numeric(14,2),
    resolution_notes            varchar(2000),
    resolved_by_id              uuid references users (id),
    resolved_at                 timestamptz,
    created_at                  timestamptz   not null default now(),
    updated_at                  timestamptz   not null default now(),
    created_by                  uuid,
    updated_by                  uuid,
    version                     bigint        not null default 0
);
create index ix_disputes_order on disputes (order_id);
create index ix_disputes_status_due on disputes (status, due_at);
create index ix_disputes_raised_by on disputes (raised_by_id);
create unique index ux_disputes_one_open_per_order on disputes (order_id) where status in ('OPEN', 'UNDER_REVIEW');

create table dispute_evidence (
    id              uuid primary key,
    dispute_id      uuid         not null references disputes (id) on delete cascade,
    submitted_by_id uuid         not null references users (id),
    kind            varchar(20)  not null,
    file_id         uuid references file_assets (id),
    note            varchar(1000),
    created_at      timestamptz  not null default now(),
    updated_at      timestamptz  not null default now(),
    created_by      uuid,
    updated_by      uuid,
    version         bigint       not null default 0
);
create index ix_dispute_evidence_dispute on dispute_evidence (dispute_id);

create table notifications (
    id              uuid primary key,
    user_id         uuid         not null references users (id) on delete cascade,
    type            varchar(40)  not null,
    title           varchar(200) not null,
    body            varchar(1000) not null,
    reference_type  varchar(30),
    reference_id    uuid,
    read_at         timestamptz,
    created_at      timestamptz  not null default now(),
    updated_at      timestamptz  not null default now(),
    created_by      uuid,
    updated_by      uuid,
    version         bigint       not null default 0
);
create index ix_notifications_user on notifications (user_id, created_at desc);
create index ix_notifications_unread on notifications (user_id) where read_at is null;

create table device_tokens (
    id           uuid primary key,
    user_id      uuid         not null references users (id) on delete cascade,
    token        varchar(512) not null unique,
    platform     varchar(10)  not null,
    last_seen_at timestamptz  not null default now(),
    created_at   timestamptz  not null default now(),
    updated_at   timestamptz  not null default now(),
    created_by   uuid,
    updated_by   uuid,
    version      bigint       not null default 0
);
create index ix_device_tokens_user on device_tokens (user_id);

create table admin_audit_logs (
    id          uuid primary key,
    admin_id    uuid         not null references users (id),
    action      varchar(60)  not null,
    entity_type varchar(40)  not null,
    entity_id   uuid,
    details     varchar(2000),
    created_at  timestamptz  not null default now()
);
create index ix_admin_audit_created on admin_audit_logs (created_at desc);
create index ix_admin_audit_entity on admin_audit_logs (entity_type, entity_id);
