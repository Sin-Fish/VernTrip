package com.vernflow.modules.tourism.repository;

import com.vernflow.modules.tourism.domain.TourismProduct;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TourismProductRepository extends JpaRepository<TourismProduct, Long> {

    List<TourismProduct> findAllByMerchantUserIdOrderByUpdatedAtDesc(Long merchantUserId);

    Optional<TourismProduct> findByIdAndMerchantUserId(Long id, Long merchantUserId);

    long countByMerchantUserIdAndStatus(Long merchantUserId, TourismProduct.Status status);

    List<TourismProduct> findAllByResourcesIdAndStatus(Long resourceId, TourismProduct.Status status);

    boolean existsByMerchantUserIdAndNameIgnoreCase(Long merchantUserId, String name);

    boolean existsByMerchantUserIdAndNameIgnoreCaseAndIdNot(Long merchantUserId, String name, Long id);
}
