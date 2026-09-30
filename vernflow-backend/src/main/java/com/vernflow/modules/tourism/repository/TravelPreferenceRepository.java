package com.vernflow.modules.tourism.repository;

import com.vernflow.modules.tourism.domain.TravelPreference;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TravelPreferenceRepository extends JpaRepository<TravelPreference, Long> { }
