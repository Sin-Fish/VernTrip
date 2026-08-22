package com.vernflow.integration.amap;

import com.vernflow.common.config.AmapWebServiceProperties;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AmapPoiClientTest {

    @Test
    void shouldParseVerifiedPoiFields() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://restapi.amap.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AmapWebServiceProperties properties = new AmapWebServiceProperties(
                URI.create("https://restapi.amap.com"),
                "test-key",
                Duration.ofSeconds(2),
                true,
                12,
                8
        );
        AmapPoiClient client = new AmapPoiClient(builder.build(), properties);
        server.expect(request -> assertThat(request.getURI().getRawQuery())
                        .contains("region=%E8%8B%8F%E5%B7%9E"))
                .andExpect(queryParam("city_limit", "true"))
                .andExpect(queryParam("show_fields", "business"))
                .andRespond(withSuccess("""
                        {
                          "status":"1",
                          "info":"OK",
                          "pois":[{
                            "id":"B020000001",
                            "name":"拙政园",
                            "type":"风景名胜;风景名胜;世界遗产",
                            "typecode":"110101",
                            "cityname":"苏州市",
                            "adname":"姑苏区",
                            "address":"东北街178号",
                            "location":"120.629120,31.326510",
                            "business":{
                              "opentime_week":"周一至周日 07:30-17:30",
                              "cost":"80",
                              "rating":"4.8",
                              "tag":"园林"
                            }
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        AmapPoiClient.AmapPoi poi = client.search("苏州", "景点").getFirst();

        assertThat(poi.id()).isEqualTo("B020000001");
        assertThat(poi.name()).isEqualTo("拙政园");
        assertThat(poi.district()).isEqualTo("姑苏区");
        assertThat(poi.longitude()).isEqualByComparingTo("120.629120");
        assertThat(poi.latitude()).isEqualByComparingTo("31.326510");
        assertThat(poi.cost()).isEqualByComparingTo("80");
        assertThat(poi.rating()).isEqualByComparingTo("4.8");
        server.verify();
    }
}
