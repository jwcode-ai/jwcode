@echo off
:: JWCode one-command launcher
:: Usage: jwcode [run|start|version|--help]

set "ROOT=%~dp0"

:: Ensure built (esbuild produces dist/cli.js)
if not exist "%ROOT%dist\cli.js" (
    echo [jwcode] First run: building...
    cd /d "%ROOT%"
    node build.mjs
)

:: Run
node "%ROOT%dist\cli.js" %* 2> "%ROOT%debug.log"
