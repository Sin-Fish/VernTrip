package com.vernflow.modules.paper.repository;

import com.vernflow.modules.paper.domain.*;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaperReferenceRepository extends JpaRepository<PaperReference, Long> {

    List<PaperReference> findByPaperIdOrderByIdAsc(Long paperId);
}

