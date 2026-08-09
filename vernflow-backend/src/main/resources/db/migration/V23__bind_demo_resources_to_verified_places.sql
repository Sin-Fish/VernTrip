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
VALUES
    ('杭州', '杭州工艺美术博物馆', '拱墅区', '小河路334号', 'B023B0I02U', 120.136930, 30.315645, 'VERIFIED', '高德 POI（2026-08-10 核验）'),
    ('杭州', '拱宸桥西历史文化街区', '拱墅区', '桥西直街1号（拱宸桥西）', 'B0FFFFZRBD', 120.137752, 30.316781, 'VERIFIED', '高德 POI（2026-08-10 核验）'),
    ('杭州', '杭州友好饭店', '上城区', '平海路53号', 'B023B02XNQ', 120.166521, 30.253702, 'VERIFIED', '高德 POI（2026-08-10 核验）'),
    ('杭州', '杭州西湖风景名胜区-湖滨公园', '上城区', '龙井路1号杭州西湖风景名胜区内（东北角）', 'B023B05MLT', 120.158818, 30.256583, 'VERIFIED', '高德 POI（2026-08-10 核验）'),
    ('杭州', '清河坊历史文化特色街区', '上城区', '河坊街180号', 'B023B07SJZ', 120.169899, 30.239827, 'VERIFIED', '高德 POI（2026-08-10 核验）'),
    ('杭州', '中国丝绸博物馆', '西湖区', '玉皇山路73-1号（近南山路）', 'B023B09JLL', 120.151292, 30.222870, 'VERIFIED', '高德 POI（2026-08-10 核验）'),
    ('杭州', '九溪烟树', '西湖区', '龙井村杭州西湖风景名胜区内（近九溪路）', 'B0FFHMH4J8', 120.113350, 30.202395, 'VERIFIED', '高德 POI（2026-08-10 核验）'),
    ('杭州', '中国茶叶博物馆（龙井馆区）', '西湖区', '满觉陇路翁家山268号', 'B023B09JN4', 120.113539, 30.219662, 'VERIFIED', '高德 POI（2026-08-10 核验）'),
    ('杭州', '小河历史文化街区', '拱墅区', '小河直街51号', 'B0FFFWQ8DS', 120.135337, 30.308302, 'VERIFIED', '高德 POI（2026-08-10 核验）'),
    ('杭州', '杭州运河祈利酒店', '拱墅区', '丽水路与大兜路交叉口西80米', 'B0HBBO6XGX', 120.145661, 30.300005, 'VERIFIED', '高德 POI（2026-08-10 核验）'),
    ('杭州', '杭州酒家（延安路店）', '上城区', '延安路205号1-3层', 'B023B1994H', 120.164014, 30.251744, 'VERIFIED', '高德 POI（2026-08-10 核验）')
ON CONFLICT (amap_poi_id) DO UPDATE SET
    name = EXCLUDED.name,
    district = EXCLUDED.district,
    address = EXCLUDED.address,
    longitude = EXCLUDED.longitude,
    latitude = EXCLUDED.latitude,
    verification_status = EXCLUDED.verification_status,
    source_name = EXCLUDED.source_name,
    updated_at = CURRENT_TIMESTAMP;

UPDATE tourism_resource SET place_id = (SELECT id FROM tourism_place WHERE amap_poi_id = 'B023B0I02U')
WHERE name IN ('中国杭州工艺美术博物馆', '拱宸桥畔手作课堂', '桥西蓝染亲子体验');

UPDATE tourism_resource SET place_id = (SELECT id FROM tourism_place WHERE amap_poi_id = 'B0FFFFZRBD')
WHERE name = '运河家常杭帮菜';

UPDATE tourism_resource SET place_id = (SELECT id FROM tourism_place WHERE amap_poi_id = 'B023B02XNQ')
WHERE name = '湖滨亲子友好酒店';

UPDATE tourism_resource SET place_id = (SELECT id FROM tourism_place WHERE amap_poi_id = 'B023B05MLT')
WHERE name = '西湖环湖慢行段';

UPDATE tourism_resource SET place_id = (SELECT id FROM tourism_place WHERE amap_poi_id = 'B023B07SJZ')
WHERE name = '清河坊宋韵夜游';

UPDATE tourism_resource SET place_id = (SELECT id FROM tourism_place WHERE amap_poi_id = 'B023B09JLL')
WHERE name = '中国丝绸博物馆';

UPDATE tourism_resource SET place_id = (SELECT id FROM tourism_place WHERE amap_poi_id = 'B0FFHMH4J8')
WHERE name = '九溪烟树轻徒步';

UPDATE tourism_resource SET place_id = (SELECT id FROM tourism_place WHERE amap_poi_id = 'B023B1994H')
WHERE name = '湖滨时令小馆';

UPDATE tourism_resource SET place_id = (SELECT id FROM tourism_place WHERE amap_poi_id = 'B023B09JN4')
WHERE name = '龙井茶园采制体验';

UPDATE tourism_resource SET place_id = (SELECT id FROM tourism_place WHERE amap_poi_id = 'B0FFFWQ8DS')
WHERE name = '小河直街午后散步';

UPDATE tourism_resource SET place_id = (SELECT id FROM tourism_place WHERE amap_poi_id = 'B0HBBO6XGX')
WHERE name = '运河文化主题酒店';

DELETE FROM tourism_place place
WHERE place.verification_status = 'APPROXIMATE'
  AND NOT EXISTS (
      SELECT 1 FROM tourism_resource resource WHERE resource.place_id = place.id
  );
