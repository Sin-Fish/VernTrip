package com.vernflow.modules.tourism.repository;

import com.vernflow.modules.tourism.domain.TourismResource;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TourismResourceRepository extends JpaRepository<TourismResource, Long> {

    interface DestinationFact {
        String getCity();
        String getName();
        String getTagsJson();
    }

    List<DestinationFact> findByStatusOrderByCityAscIdAsc(TourismResource.PublicationStatus status);

    List<TourismResource> findAllByOrderByUpdatedAtDesc();

    boolean existsByCityIgnoreCaseAndNameIgnoreCase(String city, String name);

    boolean existsByCityIgnoreCaseAndNameIgnoreCaseAndIdNot(String city, String name, Long id);

    boolean existsByPlaceId(Long placeId);

    List<TourismResource> findAllByCityIgnoreCaseAndStatusOrderByIdAsc(
            String city,
            TourismResource.PublicationStatus status
    );
}
