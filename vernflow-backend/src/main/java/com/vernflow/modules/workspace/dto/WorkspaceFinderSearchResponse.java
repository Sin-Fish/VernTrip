package com.vernflow.modules.workspace.dto;

import java.util.List;

/**
 * Search response consumed by VueFinder.
 *
 * @param files matched entries
 */
public record WorkspaceFinderSearchResponse(List<WorkspaceFinderListResponse.Entry> files) {
}
