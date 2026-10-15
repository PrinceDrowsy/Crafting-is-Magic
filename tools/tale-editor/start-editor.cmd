@echo off
rem Starts the .tale editor and opens it in your browser (any browser, Firefox included).
rem Uses the portable Node in D:\dev-tools\node if it's there, otherwise whatever Node is installed.
setlocal
cd /d "%~dp0"
if exist "D:\dev-tools\node\node.exe" set "PATH=D:\dev-tools\node;%PATH%"
if exist "D:\dev-tools" set "npm_config_cache=D:\dev-tools\npm-cache"
if not exist node_modules (
  echo Installing the editor's parts - first run only...
  call npm install --no-fund --no-audit
)
call npm run dev
