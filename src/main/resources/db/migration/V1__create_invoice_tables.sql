create table invoices (
    id uuid primary key,
    invoice_number varchar(64) not null unique,
    customer_id uuid not null,
    currency varchar(3) not null,
    due_date date not null,
    status varchar(32) not null,
    total numeric(19, 2) not null,
    paid_amount numeric(19, 2) not null,
    created_at timestamptz not null,
    version bigint not null default 0
);

create index idx_invoices_customer on invoices(customer_id);
create index idx_invoices_status on invoices(status);

create table invoice_lines (
    id uuid primary key,
    invoice_id uuid not null references invoices(id),
    description varchar(200) not null,
    quantity integer not null,
    unit_price numeric(19, 2) not null,
    amount numeric(19, 2) not null,
    constraint chk_invoice_line_quantity check (quantity > 0),
    constraint chk_invoice_line_price check (unit_price > 0)
);

create index idx_invoice_lines_invoice on invoice_lines(invoice_id);

create table payments (
    id uuid primary key,
    invoice_id uuid not null references invoices(id),
    payment_reference varchar(100) not null,
    amount numeric(19, 2) not null,
    received_at timestamptz not null,
    constraint uk_payment_invoice_reference unique(invoice_id, payment_reference),
    constraint chk_payment_amount check (amount > 0)
);

create index idx_payments_invoice on payments(invoice_id);

create table outbox_events (
    id uuid primary key,
    event_type varchar(100) not null,
    aggregate_id uuid not null,
    payload text not null,
    created_at timestamptz not null,
    published_at timestamptz,
    attempts integer not null default 0,
    last_error varchar(500)
);

create index idx_outbox_pending on outbox_events(created_at) where published_at is null;
