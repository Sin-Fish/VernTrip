from __future__ import annotations

from pydantic import BaseModel, Field


class PlanningDocumentPreview(BaseModel):
    path: str
    citeLabel: str
    sectionTitle: str
    chunkCount: int
    excerpt: str


class PlanningToolOption(BaseModel):
    id: str
    name: str
    summary: str
    type: str
    positionClass: str
    branchTool: bool
    readWorkspace: bool
    writeWorkspace: bool
    useNetwork: bool


class WorkbenchPlanRequest(BaseModel):
    prompt: str
    scopeMode: str
    workingFileCount: int
    totalScopeFileCount: int
    readableDocumentCount: int
    workingFileLabels: list[str] = Field(default_factory=list)
    unreadableFiles: list[str] = Field(default_factory=list)
    documents: list[PlanningDocumentPreview] = Field(default_factory=list)
    availableTools: list[PlanningToolOption] = Field(default_factory=list)


class WorkbenchPlanNodeNote(BaseModel):
    toolId: str
    title: str
    detail: str
    rationale: str


class WorkbenchPlanResponse(BaseModel):
    title: str
    summary: str
    inspectMode: str
    mainTools: list[str] = Field(default_factory=list)
    branchTools: list[str] = Field(default_factory=list)
    nodeNotes: list[WorkbenchPlanNodeNote] = Field(default_factory=list)
