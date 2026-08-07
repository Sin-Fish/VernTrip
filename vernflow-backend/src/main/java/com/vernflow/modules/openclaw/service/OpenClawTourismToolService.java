package com.vernflow.modules.openclaw.service;

import com.vernflow.modules.openclaw.dto.OpenClawTourismCatalogResponse;
import com.vernflow.modules.tourism.dto.TourismResourceResponse;
import com.vernflow.modules.tourism.service.TravelPlanningService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class OpenClawTourismToolService {

    private static final String DEFAULT_CITY = "杭州";

    private final OpenClawSessionContextService sessionContextService;
    private final TravelPlanningService travelPlanningService;

    public OpenClawTourismToolService(
            OpenClawSessionContextService sessionContextService,
            TravelPlanningService travelPlanningService
    ) {
        this.sessionContextService = sessionContextService;
        this.travelPlanningService = travelPlanningService;
    }

    @Transactional
    public OpenClawTourismCatalogResponse readCatalog(String sessionKey, String city) {
        sessionContextService.resolveActive(sessionKey);
        String targetCity = StringUtils.hasText(city) ? city.trim() : DEFAULT_CITY;
        List<TourismResourceResponse> resources = travelPlanningService.listResources(targetCity);
        return new OpenClawTourismCatalogResponse(
                targetCity,
                !resources.isEmpty(),
                resources.size(),
                resources
        );
    }
}
