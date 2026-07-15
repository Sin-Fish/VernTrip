package com.vernflow.modules.paper.repository;

import com.vernflow.modules.paper.domain.*;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaperRelationRepository extends JpaRepository<PaperRelation, Long> {

    List<PaperRelation> findBySourcePaperIdOrTargetPaperId(Long sourcePaperId, Long targetPaperId);

    List<PaperRelation> findAllBySourcePaperIdOrTargetPaperIdOrderByConfidenceDesc(Long sourcePaperId, Long targetPaperId);

    long countBySourcePaperId(Long sourcePaperId);

    long countByTargetPaperId(Long targetPaperId);
}

