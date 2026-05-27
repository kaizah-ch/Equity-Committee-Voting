@echo off
setlocal EnableDelayedExpansion

set "JAVA_HOME=C:\Program Files\Java\jdk-21"
set "PATH=%JAVA_HOME%\bin;%PATH%"

if exist "%~dp0.env" (
  for /f "usebackq tokens=1,* delims==" %%A in ("%~dp0.env") do (
    set "ENV_KEY=%%A"
    set "ENV_VALUE=%%B"
    if not "!ENV_KEY!"=="" if not "!ENV_KEY:~0,1!"=="#" set "!ENV_KEY!=!ENV_VALUE!"
  )
)

cd /d "%~dp0backend"
call mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=%SPRING_PROFILES_ACTIVE%"

endlocal
