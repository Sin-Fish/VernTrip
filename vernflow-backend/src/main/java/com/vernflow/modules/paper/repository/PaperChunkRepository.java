package com.vernflow.modules.paper.repository;

import com.vernflow.modules.paper.domain.*;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PaperChunkRepository extends JpaRepository<PaperChunk, Long> {

    List<PaperChunk> findAllByPaperIdInOrderByPaperIdAscSortIndexAsc(Collection<Long> paperIds);
}

