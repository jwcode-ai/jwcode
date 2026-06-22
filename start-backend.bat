@echo off
:: Start JWCode backend in current window
:: Called by restart.bat or standalone
chcp 65001 >nul
set "JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 %JAVA_TOOL_OPTIONS%"
cd /d "%~dp0"
echo Starting JWCode Backend...
echo Building...
call mvn install -pl jwcode-core,jwcode-web -am -DskipTests -q
if errorlevel 1 (
    echo ERROR: Compile failed!
    pause
    exit /b 1
)
echo Starting server...
mvn exec:java -pl jwcode-web -Dexec.mainClass=com.jwcode.web.WebLauncher -Dexec.jvmArgs="-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8" -Dexec.args="8080 8081"
echo.
echo Backend stopped.
pause
