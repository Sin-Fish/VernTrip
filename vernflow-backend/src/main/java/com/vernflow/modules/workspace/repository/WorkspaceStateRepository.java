package com.vernflow.modules.workspace.repository;

import com.vernflow.modules.workspace.domain.WorkspaceState;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for the singleton workspace-shell state row.
 */
public interface WorkspaceStateRepository extends JpaRepository<WorkspaceState, Long> {
}
