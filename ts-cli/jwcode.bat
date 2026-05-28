@echo off
:: JWCode one-command launcher
:: Usage: jwcode [run|start|version|--help]

set "ROOT=%~dp0"

:: Ensure built
if not exist "%ROOT%dist\main.js" (
    echo [jwcode] First run: compiling...
    cd /d "%ROOT%"
    call npx tsc 2>nul
)

:: Run
node "%ROOT%dist\main.js" %*
