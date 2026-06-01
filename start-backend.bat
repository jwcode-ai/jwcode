@echo off
:: Start JWCode backend in current window
:: Called by restart.bat or standalone
cd /d "%~dp0"
echo Starting JWCode Backend...
echo Compiling...
call mvn compile -pl jwcode-core,jwcode-web -am -q
if errorlevel 1 (
    echo ERROR: Compile failed!
    pause
    exit /b 1
)
echo Starting server...
mvn exec:java -pl jwcode-web -Dexec.mainClass=com.jwcode.web.WebLauncher -Dexec.args="8080 8081"
echo.
echo Backend stopped.
pause
