CREATE TABLE travel_preference (
    user_id BIGINT PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
    interests_json TEXT NOT NULL,
    visited_cities_json TEXT NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
