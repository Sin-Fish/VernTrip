package com.vernflow.modules.openclaw.service;

import com.vernflow.common.config.OpenClawProperties;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenClawGatewayClientTest {

    @Test
    void shouldCallOpenResponsesWithTrustedSessionAndParseText() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://127.0.0.1:18789");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenClawProperties properties = new OpenClawProperties(
                "shadow",
                URI.create("http://127.0.0.1:18789"),
                "gateway-secret",
                "verntrip-main",
                Duration.ofMinutes(3),
                "service-secret",
                Duration.ofMinutes(30),
                1.0,
                Duration.ofSeconds(60),
                1200,
                12000
        );
        OpenClawGatewayClient client = new OpenClawGatewayClient(builder.build(), properties);

        server.expect(once(), requestTo("http://127.0.0.1:18789/v1/responses"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer gateway-secret"))
                .andExpect(header("x-openclaw-agent-id", "verntrip-main"))
                .andExpect(header("x-openclaw-session-key", "verntrip:trusted-session"))
                .andExpect(content().json("""
                        {
                          "model":"openclaw/verntrip-main",
                          "input":"请总结当前工作区",
                          "stream":false,
                          "max_output_tokens":1200
                        }
                        """, false))
                .andRespond(withSuccess("""
                        {
                          "id":"resp_shadow_1",
                          "output":[
                            {
                              "type":"message",
                              "content":[{"type":"output_text","text":"这是影子答案。"}]
                            }
                          ],
                          "usage":{"input_tokens":42,"output_tokens":12}
                        }
                        """, MediaType.APPLICATION_JSON));

        OpenClawGatewayClient.ShadowResponse response = client.createShadowResponse(
                "verntrip:trusted-session",
                "请总结当前工作区"
        );

        assertThat(response.responseId()).isEqualTo("resp_shadow_1");
        assertThat(response.text()).isEqualTo("这是影子答案。");
        assertThat(response.inputTokens()).isEqualTo(42L);
        assertThat(response.outputTokens()).isEqualTo(12L);
        server.verify();
    }
}
