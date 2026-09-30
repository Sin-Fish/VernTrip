package com.vernflow.modules.tourism.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vernflow.common.config.OpenClawProperties;
import com.vernflow.common.exception.BusinessException;
import com.vernflow.modules.openclaw.service.OpenClawGatewayClient;
import com.vernflow.modules.openclaw.service.OpenClawSessionContextService;
import com.vernflow.modules.tourism.domain.*;
import com.vernflow.modules.tourism.dto.*;
import com.vernflow.modules.tourism.repository.*;
import com.vernflow.modules.user.service.UserAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TravelFortuneServiceTest {
    private final TravelPreferenceRepository profiles = mock(TravelPreferenceRepository.class);
    private final TripRequestRepository requests = mock(TripRequestRepository.class);
    private final TourismResourceRepository resources = mock(TourismResourceRepository.class);
    private final TravelPlanningService planning = mock(TravelPlanningService.class);
    private final OpenClawProperties properties = mock(OpenClawProperties.class);
    private final OpenClawSessionContextService sessions = mock(OpenClawSessionContextService.class);
    private final OpenClawGatewayClient gateway = mock(OpenClawGatewayClient.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final TravelFortuneService service = new TravelFortuneService(profiles, requests, resources,
            planning, mock(UserAuthService.class), properties, sessions, gateway, mapper);

    @BeforeEach
    void setUp() {
        when(properties.primaryEnabled()).thenReturn(true);
        when(sessions.bind(eq(7L), isNull(), eq(false), eq(List.of())))
                .thenReturn(new OpenClawSessionContextService.BoundSession("fortune-test", LocalDateTime.now().plusMinutes(10)));
        var candidates = List.of(fact("杭州"), fact("苏州"), fact("南京"));
        when(resources.findByStatusOrderByCityAscIdAsc(TourismResource.PublicationStatus.PUBLISHED))
                .thenReturn(candidates);
        when(requests.findSavedHistory(eq(7L), anyList(), any())).thenReturn(List.of(history()));
        when(planning.createPlan(eq(7L), any())).thenReturn(mock(TravelPlanResponse.class));
        respond("南京");
    }

    @Test
    void choosesCityFromInterestsAndHistoryThenBuildsFullPlan() throws Exception {
        var result = service.draw(7L, request(true));
        var prompt = ArgumentCaptor.forClass(String.class);
        verify(gateway).createResponse(eq("fortune-test"), prompt.capture(), anyString());
        var facts = mapper.readTree(prompt.getValue());
        assertThat(facts.path("candidates").has("杭州")).isFalse();
        assertThat(facts.path("candidates").has("南京")).isTrue();
        assertThat(facts.path("explicitInterests").get(0).asText()).isEqualTo("博物馆");
        assertThat(facts.path("confirmedVisitedCities").get(0).asText()).isEqualTo("杭州市");
        assertThat(facts.path("savedTravelIntentions").get(0).path("destination").asText()).isEqualTo("苏州");
        var planRequest = ArgumentCaptor.forClass(TravelPlanCreateRequest.class);
        verify(planning).createPlan(eq(7L), planRequest.capture());
        assertThat(planRequest.getValue().destination()).isEqualTo("南京");
        assertThat(planRequest.getValue().days()).isEqualTo(3);
        assertThat(planRequest.getValue().budget()).isEqualByComparingTo("4200");
        assertThat(planRequest.getValue().interests()).containsExactly("博物馆", "本地美食");
        assertThat(result.selectionEngine()).isEqualTo("openclaw");
        verify(profiles).save(any(TravelPreference.class));
        verify(requests).findSavedHistory(eq(7L), anyList(), any());
    }

    @Test
    void rejectsInventedOrExcludedDestinationsInsteadOfSilentlyReturningLocalAttraction() {
        respond("杭州");
        assertThatThrownBy(() -> service.draw(7L, request(true))).isInstanceOf(BusinessException.class);
        respond("不存在的城市");
        assertThatThrownBy(() -> service.draw(7L, request(true))).isInstanceOf(BusinessException.class);
        verifyNoInteractions(planning);
        verify(profiles, never()).save(any());
    }

    @Test
    void allowsRevisitingOnlyWhenUserDisablesNewDestinationPreference() {
        respond("杭州");
        service.draw(7L, request(false));
        verify(planning).createPlan(eq(7L), argThat(r -> r.destination().equals("杭州")));
    }

    @Test
    void savedPlansNeverBecomeConfirmedVisits() {
        var profile = service.profile(7L);
        assertThat(profile.savedDestinations()).containsExactly("苏州");
        assertThat(profile.visitedCities()).isEmpty();
        assertThat(profile.interests()).containsExactly("人文历史");
        when(profiles.findById(7L)).thenReturn(Optional.of(new TravelPreference(7L, "[\"摄影\"]", "[\"北京\"]")));
        assertThat(service.profile(7L).visitedCities()).containsExactly("北京");
        assertThat(service.profile(7L).interests()).containsExactly("摄影");
        verify(profiles, atLeastOnce()).findById(7L);
    }

    @Test
    void missingAiNeverProducesFakeAiPlan() {
        when(properties.primaryEnabled()).thenReturn(false);
        assertThatThrownBy(() -> service.draw(7L, request(true))).isInstanceOf(BusinessException.class);
        verifyNoInteractions(planning, gateway);
    }

    private FortuneDrawRequest request(boolean exploreNew) {
        return new FortuneDrawRequest(new FortuneProfileRequest(List.of("博物馆", "本地美食"), List.of("杭州市")),
                "上海", LocalDate.now().plusDays(7), 3, "朋友结伴", BigDecimal.valueOf(4200), "轻松一些", exploreNew, "");
    }

    private TripRequest history() {
        return TripRequest.create(7L, "苏州", "上海", LocalDate.now(), 2, "朋友结伴",
                BigDecimal.valueOf(3000), "轻松一些", "[\"人文历史\"]", "");
    }

    private TourismResourceRepository.DestinationFact fact(String city) {
        var fact = mock(TourismResourceRepository.DestinationFact.class);
        when(fact.getCity()).thenReturn(city);
        when(fact.getName()).thenReturn(city + "博物馆");
        when(fact.getTagsJson()).thenReturn("[\"文化\"]");
        return fact;
    }

    private void respond(String city) {
        when(gateway.createResponse(anyString(), anyString(), anyString())).thenReturn(
                new OpenClawGatewayClient.ShadowResponse("test", "{\"destination\":\"" + city
                        + "\",\"reason\":\"适合博物馆与街巷漫游\",\"theme\":\"城市记忆\"}", 100L, 100L));
    }
}
