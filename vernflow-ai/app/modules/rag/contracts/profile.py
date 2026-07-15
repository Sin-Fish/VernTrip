from __future__ import annotations

from dataclasses import dataclass

from .generator import Generator
from .graph_strategy import GraphStrategy
from .retriever import Retriever
from .selector import Selector


@dataclass(frozen=True)
class RagProfile:
    profile_id: str
    retriever: Retriever
    selector: Selector
    graph_strategy: GraphStrategy
    generator: Generator
