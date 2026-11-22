create table notification_deliveries (
    event_id uuid primary key,
    invoice_id uuid not null,
    event_type varchar(100) not null,
    recipient varchar(320) not null,
    subject varchar(200) not null,
    status varchar(16) not null,
    attempts integer not null default 0,
    last_error varchar(500),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint chk_notification_status check (status in ('PENDING', 'SENT', 'FAILED')),
    constraint chk_notification_attempts check (attempts >= 0)
);

create index idx_notifications_invoice on notification_deliveries(invoice_id);
create index idx_notifications_status on notification_deliveries(status);
