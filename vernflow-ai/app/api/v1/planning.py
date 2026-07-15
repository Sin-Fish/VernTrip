from __future__ import annotations

from fastapi import APIRouter

from ...modules.planning.schemas import WorkbenchPlanRequest, WorkbenchPlanResponse
from ...modules.planning.service import build_workbench_plan


router = APIRouter()


@router.post("/ai/plan-workbench", response_model=WorkbenchPlanResponse)
def plan_workbench_endpoint(request: WorkbenchPlanRequest) -> WorkbenchPlanResponse:
    payload = build_workbench_plan(request)
    return WorkbenchPlanResponse(**payload)
