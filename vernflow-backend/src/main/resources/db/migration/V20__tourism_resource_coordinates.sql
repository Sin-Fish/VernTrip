ALTER TABLE tourism_resource
    ADD COLUMN longitude NUMERIC(10, 6),
    ADD COLUMN latitude NUMERIC(10, 6);

UPDATE tourism_resource SET longitude = 120.150114, latitude = 30.320335 WHERE city = '杭州' AND name = '中国杭州工艺美术博物馆';
UPDATE tourism_resource SET longitude = 120.148541, latitude = 30.316932 WHERE city = '杭州' AND name = '拱宸桥畔手作课堂';
UPDATE tourism_resource SET longitude = 120.146500, latitude = 30.317800 WHERE city = '杭州' AND name = '运河家常杭帮菜';
UPDATE tourism_resource SET longitude = 120.164750, latitude = 30.257250 WHERE city = '杭州' AND name = '湖滨亲子友好酒店';
UPDATE tourism_resource SET longitude = 120.157300, latitude = 30.252700 WHERE city = '杭州' AND name = '西湖环湖慢行段';
UPDATE tourism_resource SET longitude = 120.171700, latitude = 30.237500 WHERE city = '杭州' AND name = '清河坊宋韵夜游';
UPDATE tourism_resource SET longitude = 120.147500, latitude = 30.226400 WHERE city = '杭州' AND name = '中国丝绸博物馆';
UPDATE tourism_resource SET longitude = 120.117500, latitude = 30.202600 WHERE city = '杭州' AND name = '九溪烟树轻徒步';
UPDATE tourism_resource SET longitude = 120.162900, latitude = 30.254700 WHERE city = '杭州' AND name = '湖滨时令小馆';
UPDATE tourism_resource SET longitude = 120.103900, latitude = 30.223800 WHERE city = '杭州' AND name = '龙井茶园采制体验';
UPDATE tourism_resource SET longitude = 120.142500, latitude = 30.307400 WHERE city = '杭州' AND name = '小河直街午后散步';
UPDATE tourism_resource SET longitude = 120.151300, latitude = 30.312500 WHERE city = '杭州' AND name = '运河文化主题酒店';
UPDATE tourism_resource SET longitude = 120.145700, latitude = 30.317200 WHERE city = '杭州' AND name = '桥西蓝染亲子体验';
