package com.vernflow.modules.tourism.repository;

import com.vernflow.modules.tourism.domain.TourismResource;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TourismResourceRepository extends JpaRepository<TourismResource, Long> {

    List<TourismResource> findAllByOrderByUpdatedAtDesc();

    boolean existsByCityIgnoreCaseAndNameIgnoreCase(String city, String name);

    boolean existsByCityIgnoreCaseAndNameIgnoreCaseAndIdNot(String city, String name, Long id);

    List<TourismResource> findAllByCityIgnoreCaseAndStatusOrderByIdAsc(
            String city,
            TourismResource.PublicationStatus status
    );
}
