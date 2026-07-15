from __future__ import annotations

from .api.v1.planning import router as planning_router
from .app_factory import create_ai_app


app = create_ai_app(
    title="VernFlow Agent Service",
    description="VernFlow 的 Agent 服务，负责工作台任务规划、执行策略与多步决策。",
    routers=[planning_router],
    service_kind="agent",
    warmup_embeddings=False,
)
