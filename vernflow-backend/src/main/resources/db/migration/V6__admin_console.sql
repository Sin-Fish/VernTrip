create table if not exists admin_operation_log (
    id bigserial primary key,
    actor varchar(128) not null,
    action varchar(128) not null,
    target_type varchar(64) not null,
    target_path text,
    detail text,
    created_at timestamp not null default current_timestamp
);

create index if not exists idx_admin_operation_log_created_at
    on admin_operation_log (created_at desc);
