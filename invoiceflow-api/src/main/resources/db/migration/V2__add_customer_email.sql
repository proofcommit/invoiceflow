alter table invoices add column customer_email varchar(320);

update invoices
set customer_email = 'legacy-customer@example.invalid'
where customer_email is null;

alter table invoices alter column customer_email set not null;
