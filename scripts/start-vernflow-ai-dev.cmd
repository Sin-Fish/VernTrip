@echo off
cd /d "%~dp0..\vernflow-ai"
set "RAG_EMBEDDING_BACKEND=hash"
python -m uvicorn app.main:app --host 127.0.0.1 --port 8000
