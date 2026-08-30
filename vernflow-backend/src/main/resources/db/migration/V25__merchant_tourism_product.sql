CREATE TABLE tourism_product (
    id BIGSERIAL PRIMARY KEY,
    merchant_user_id BIGINT NOT NULL REFERENCES app_user(id),
    name VARCHAR(180) NOT NULL,
    target_audience VARCHAR(120) NOT NULL,
    duration_days INTEGER NOT NULL,
    price NUMERIC(12, 2) NOT NULL,
    description TEXT NOT NULL,
    highlights_json TEXT NOT NULL,
    status VARCHAR(24) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE tourism_product_resource (
    product_id BIGINT NOT NULL REFERENCES tourism_product(id) ON DELETE CASCADE,
    resource_id BIGINT NOT NULL REFERENCES tourism_resource(id),
    PRIMARY KEY (product_id, resource_id)
);

CREATE INDEX idx_tourism_product_merchant_status
    ON tourism_product(merchant_user_id, status, updated_at DESC);

CREATE INDEX idx_tourism_product_resource_resource
    ON tourism_product_resource(resource_id);
