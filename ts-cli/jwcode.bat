@echo off
:: JWCode one-command launcher
:: Usage: jwcode [run|start|version|--help]
:: Requires: Bun (npm install -g bun)

set "ROOT=%~dp0"

:: Check if Bun is available
where bun >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo [jwcode] Bun is required. Install with: npm install -g bun
    pause
    exit /b 1
)

:: Ensure built (Bun produces dist/cli.js)
if not exist "%ROOT%dist\cli.js" (
    echo [jwcode] First run: building...
    cd /d "%ROOT%"
    bun run build.mjs
    if %ERRORLEVEL% neq 0 (
        echo [jwcode] Build failed.
        pause
        exit /b 1
    )
)

:: Run with Bun
bun --conditions browser "%ROOT%dist\cli.js" %*
