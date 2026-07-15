package com.vernflow.modules.paper.repository;

import com.vernflow.modules.paper.domain.*;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExternalPaperRepository extends JpaRepository<ExternalPaper, Long> {

    List<ExternalPaper> findAllByStatusOrderByCreatedAtDesc(ExternalPaperStatus status);
}

