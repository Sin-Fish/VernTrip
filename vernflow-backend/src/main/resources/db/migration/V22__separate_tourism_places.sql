CREATE TABLE tourism_place (
    id BIGSERIAL PRIMARY KEY,
    city VARCHAR(80) NOT NULL,
    name VARCHAR(160) NOT NULL,
    district VARCHAR(80),
    address VARCHAR(255),
    amap_poi_id VARCHAR(64),
    longitude NUMERIC(10, 6) NOT NULL,
    latitude NUMERIC(10, 6) NOT NULL,
    verification_status VARCHAR(24) NOT NULL,
    source_name VARCHAR(160) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_tourism_place_amap_poi_id
    ON tourism_place(amap_poi_id);

CREATE INDEX idx_tourism_place_city_name
    ON tourism_place(city, name);

ALTER TABLE tourism_resource
    ADD COLUMN place_id BIGINT;

INSERT INTO tourism_place (
    city,
    name,
    district,
    address,
    amap_poi_id,
    longitude,
    latitude,
    verification_status,
    source_name
)
SELECT
    city,
    name,
    district,
    COALESCE(address, district, city),
    amap_poi_id,
    COALESCE(longitude, 120.155100),
    COALESCE(latitude, 30.274100),
    CASE WHEN amap_poi_id IS NOT NULL THEN 'VERIFIED' ELSE 'APPROXIMATE' END,
    CASE WHEN amap_poi_id IS NOT NULL THEN '高德 POI' ELSE '历史资源坐标迁移' END
FROM tourism_resource;

UPDATE tourism_resource resource
SET place_id = place.id
FROM tourism_place place
WHERE place.city = resource.city
  AND place.name = resource.name;

ALTER TABLE tourism_resource
    ADD CONSTRAINT fk_tourism_resource_place
        FOREIGN KEY (place_id) REFERENCES tourism_place(id);

ALTER TABLE tourism_resource
    ALTER COLUMN place_id SET NOT NULL;

CREATE INDEX idx_tourism_resource_place_id
    ON tourism_resource(place_id);
