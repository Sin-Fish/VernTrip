ALTER TABLE tourism_resource
    ADD COLUMN amap_poi_id VARCHAR(64);

CREATE INDEX idx_tourism_resource_amap_poi_id
    ON tourism_resource(amap_poi_id);
