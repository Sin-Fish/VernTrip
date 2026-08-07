package com.vernflow.modules.openclaw;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vernflow.modules.openclaw.domain.OpenClawShadowRun;
import com.vernflow.modules.openclaw.repository.OpenClawShadowRunRepository;
import com.vernflow.modules.openclaw.service.OpenClawGatewayClient;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:openclaw-shadow-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.rag-store.datasource.url=jdbc:h2:mem:openclaw-shadow-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.rag-store.datasource.username=sa",
        "app.rag-store.datasource.password=",
        "app.rag-store.datasource.driver-class-name=org.h2.Driver",
        "app.rag-store.flyway.enabled=false",
        "app.storage.workspace-root-dir=./build/test-storage/openclaw-shadow",
        "app.ai.base-url=http://127.0.0.1:65530",
        "app.user.auth-required=false",
        "app.openclaw.engine=shadow",
        "app.openclaw.gateway-token=test-gateway-token",
        "app.openclaw.service-token=test-service-token",
        "app.openclaw.shadow-sample-rate=1.0"
})
@AutoConfigureMockMvc
class OpenClawShadowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OpenClawShadowRunRepository shadowRunRepository;

    @MockBean
    private OpenClawGatewayClient gatewayClient;

    private CountDownLatch releaseGateway;

    @AfterEach
    void tearDown() {
        if (releaseGateway != null) {
            releaseGateway.countDown();
        }
        shadowRunRepository.deleteAll();
    }

    @Test
    void shouldReturnLegacyAnswerBeforeShadowRunCompletes() throws Exception {
        CountDownLatch gatewayStarted = new CountDownLatch(1);
        releaseGateway = new CountDownLatch(1);
        given(gatewayClient.createShadowResponse(anyString(), anyString())).willAnswer(invocation -> {
            gatewayStarted.countDown();
            if (!releaseGateway.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Test gateway was not released.");
            }
            return new OpenClawGatewayClient.ShadowResponse(
                    "resp_shadow_integration",
                    "OpenClaw 的影子回答",
                    30L,
                    9L
            );
        });

        String body = mockMvc.perform(post("/api/assistant/sessions/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "prompt":"介绍一下当前工作区",
                                  "globalScope":false,
                                  "workingFiles":[]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages[1].role").value("assistant"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        long assistantSessionId = objectMapper.readTree(body).path("data").path("id").asLong();
        assertThat(gatewayStarted.await(3, TimeUnit.SECONDS)).isTrue();

        OpenClawShadowRun running = awaitRunStatus(OpenClawShadowRun.STATUS_RUNNING, Duration.ofSeconds(3));
        assertThat(running.getAssistantSessionId()).isEqualTo(assistantSessionId);
        assertThat(running.getRequestMessageId()).startsWith("user-");
        assertThat(running.getLegacyMessageId()).startsWith("assistant-");
        assertThat(running.getSessionKey()).startsWith("verntrip:");

        releaseGateway.countDown();

        OpenClawShadowRun succeeded = awaitRunStatus(OpenClawShadowRun.STATUS_SUCCEEDED, Duration.ofSeconds(3));
        assertThat(succeeded.getResponseId()).isEqualTo("resp_shadow_integration");
        assertThat(succeeded.getResponseText()).isEqualTo("OpenClaw 的影子回答");
        assertThat(succeeded.getInputTokens()).isEqualTo(30L);
        assertThat(succeeded.getOutputTokens()).isEqualTo(9L);
        assertThat(succeeded.getErrorCode()).isNull();
    }

    private OpenClawShadowRun awaitRunStatus(String status, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            List<OpenClawShadowRun> runs = shadowRunRepository.findAll();
            if (runs.size() == 1 && status.equals(runs.getFirst().getStatus())) {
                return runs.getFirst();
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Shadow run did not reach status " + status);
    }
}
