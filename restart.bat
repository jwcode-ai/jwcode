@echo off
setlocal enabledelayedexpansion

set "ROOT=%~dp0"

echo ========================================
echo   JWCode Restart
echo ========================================
echo.

:: 1. Kill old backend on port 8080 / 8081
echo [1/4] Stopping old backend...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8080.*LISTENING" 2^>nul') do (
    powershell -Command "Stop-Process -Id %%a -Force -ErrorAction SilentlyContinue" 2>nul
    if !errorlevel! == 0 echo   Stopped PID %%a
)
timeout /t 2 /nobreak >nul
echo   Done.

:: 2. Build frontend
echo [2/4] Building frontend...
cd /d "%ROOT%jwcode-web"
call npm run build
if errorlevel 1 (
    echo   ERROR: Frontend build failed!
    pause
    exit /b 1
)
echo   Done.

:: 3. Compile backend
echo [3/4] Compiling backend...
cd /d "%ROOT%jwcode-parent"
call mvn compile -pl ../jwcode-core,../jwcode-web -am -q
if errorlevel 1 (
    echo   ERROR: Backend compile failed!
    pause
    exit /b 1
)
echo   Done.

:: 4. Start backend in new window
echo [4/4] Starting backend...
start "JWCode-Backend" cmd /k "%ROOT%start-backend.bat"
echo.
echo Waiting for backend (port 8080)...
echo.

:: 5. Wait for backend using PowerShell (more reliable)
powershell -Command "$i=0; while($i -lt 30) { Start-Sleep 2; $i++; Write-Host \"  Waiting... $i\"; $c = netstat -ano | Select-String \"\":8080.*LISTENING\"\"; if($c) { Write-Host \"Backend is ready!\"; Start-Process http://localhost:8080; exit 0 } } Write-Host \"WARNING: Backend may not have started. Check the JWCode-Backend window.\"; Read-Host \"Press Enter to exit\""

:end

endlocal
