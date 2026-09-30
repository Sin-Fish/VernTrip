package com.vernflow.modules.tourism.dto;

import java.util.List;

public record FortuneProfileResponse(
        List<String> interests, List<String> visitedCities, List<String> savedDestinations
) { }
