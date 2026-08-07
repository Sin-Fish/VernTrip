CREATE TABLE tourism_resource (
    id BIGSERIAL PRIMARY KEY,
    city VARCHAR(80) NOT NULL,
    name VARCHAR(160) NOT NULL,
    category VARCHAR(32) NOT NULL,
    district VARCHAR(80),
    summary TEXT NOT NULL,
    tags_json TEXT NOT NULL,
    suitable_people_json TEXT NOT NULL,
    address VARCHAR(255),
    source_name VARCHAR(160) NOT NULL,
    source_url TEXT,
    open_hours VARCHAR(160),
    price_min NUMERIC(12, 2) NOT NULL DEFAULT 0,
    price_max NUMERIC(12, 2) NOT NULL DEFAULT 0,
    status VARCHAR(24) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_tourism_resource_city_name
    ON tourism_resource (city, name);

CREATE INDEX idx_tourism_resource_city_status
    ON tourism_resource (city, status, category);

CREATE TABLE trip_request (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    destination VARCHAR(80) NOT NULL,
    departure_city VARCHAR(80),
    travel_date DATE NOT NULL,
    days INTEGER NOT NULL,
    traveler_type VARCHAR(40) NOT NULL,
    budget NUMERIC(12, 2) NOT NULL,
    pace VARCHAR(40) NOT NULL,
    interests_json TEXT NOT NULL,
    notes TEXT,
    status VARCHAR(24) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_trip_request_user_created
    ON trip_request (user_id, created_at DESC);

CREATE TABLE travel_plan (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    trip_request_id BIGINT NOT NULL REFERENCES trip_request(id) ON DELETE CASCADE,
    parent_plan_id BIGINT REFERENCES travel_plan(id) ON DELETE SET NULL,
    version INTEGER NOT NULL,
    title VARCHAR(180) NOT NULL,
    summary TEXT NOT NULL,
    status VARCHAR(24) NOT NULL,
    match_score INTEGER NOT NULL,
    estimated_budget NUMERIC(12, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_travel_plan_user_updated
    ON travel_plan (user_id, updated_at DESC);

CREATE INDEX idx_travel_plan_request_version
    ON travel_plan (trip_request_id, version DESC);

CREATE TABLE travel_plan_item (
    id BIGSERIAL PRIMARY KEY,
    plan_id BIGINT NOT NULL REFERENCES travel_plan(id) ON DELETE CASCADE,
    resource_id BIGINT REFERENCES tourism_resource(id) ON DELETE SET NULL,
    day_number INTEGER NOT NULL,
    sequence_no INTEGER NOT NULL,
    start_time VARCHAR(8) NOT NULL,
    label VARCHAR(80) NOT NULL,
    title VARCHAR(180) NOT NULL,
    description TEXT NOT NULL,
    tag VARCHAR(40) NOT NULL,
    estimated_cost NUMERIC(12, 2) NOT NULL DEFAULT 0,
    source_name VARCHAR(160),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_travel_plan_item_plan_order
    ON travel_plan_item (plan_id, day_number, sequence_no);

CREATE TABLE trip_feedback (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    plan_id BIGINT NOT NULL REFERENCES travel_plan(id) ON DELETE CASCADE,
    feedback_type VARCHAR(24) NOT NULL,
    note TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_trip_feedback_user_plan_type
    ON trip_feedback (user_id, plan_id, feedback_type);

CREATE INDEX idx_trip_feedback_user_created
    ON trip_feedback (user_id, created_at DESC);
