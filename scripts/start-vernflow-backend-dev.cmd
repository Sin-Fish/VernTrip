@echo off
cd /d "%~dp0.."
set "GRADLE_USER_HOME=%CD%\.gradle-home-rag"
gradlew.bat :vernflow-backend:bootRun --no-daemon --console=plain --no-configuration-cache --project-cache-dir "%CD%\.gradle-project-cache"
