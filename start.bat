@echo off
echo === JWCode Launcher ===
echo.

REM Check Java
java -version >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Java not found. Please install JDK 17+.
    pause
    exit /b 1
)

REM Check Maven
mvn -version >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Maven not found. Please install Maven 3.8+.
    pause
    exit /b 1
)

REM Check Node
node -v >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Node.js not found. Please install Node 18+.
    pause
    exit /b 1
)

echo [1/2] Installing npm dependencies...
cd /d "%~dp0ts-cli"
call npm install --silent

echo [2/2] Building and starting JWCode...
call npm start
