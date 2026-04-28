@echo off
setlocal enabledelayedexpansion

rem Lightweight Gradle bootstrap for this repo (no wrapper jar required).
rem Reads distributionUrl from gradle/wrapper/gradle-wrapper.properties,
rem downloads the Gradle distribution (if needed) and runs gradle.bat.

set "APP_HOME=%~dp0"
set "PROPS_FILE=%APP_HOME%gradle\wrapper\gradle-wrapper.properties"

if not exist "%PROPS_FILE%" (
  echo ERROR: Missing "%PROPS_FILE%".
  exit /b 1
)

set "DIST_URL="
for /f "usebackq tokens=1,* delims==" %%A in ("%PROPS_FILE%") do (
  if /i "%%A"=="distributionUrl" set "DIST_URL=%%B"
)

if "%DIST_URL%"=="" (
  echo ERROR: Could not read distributionUrl from "%PROPS_FILE%".
  exit /b 1
)

rem Unescape the 'https\://...' form used in properties files.
set "DIST_URL=%DIST_URL:\://=://%"

for %%F in ("%DIST_URL%") do set "DIST_ZIP_NAME=%%~nxF"
set "DIST_VERSION="
for /f "tokens=2 delims=-" %%V in ("%DIST_ZIP_NAME%") do set "DIST_VERSION=%%V"
set "DIST_VERSION=%DIST_VERSION:.zip=%"

set "BOOTSTRAP_DIR=%APP_HOME%.gradle-bootstrap"
set "ZIP_PATH=%BOOTSTRAP_DIR%\%DIST_ZIP_NAME%"
set "UNPACK_DIR=%BOOTSTRAP_DIR%\gradle-%DIST_VERSION%"
set "GRADLE_BIN=%UNPACK_DIR%\bin\gradle.bat"

set "PS_EXE=%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe"
if not exist "%PS_EXE%" set "PS_EXE=pwsh"

if not exist "%GRADLE_BIN%" (
  if not exist "%BOOTSTRAP_DIR%" mkdir "%BOOTSTRAP_DIR%" >nul 2>nul

  if not exist "%ZIP_PATH%" (
    echo Downloading Gradle "%DIST_VERSION%"...
    "%PS_EXE%" -NoProfile -ExecutionPolicy Bypass -Command ^
      "$p='%ZIP_PATH%'; $u='%DIST_URL%';" ^
      "try { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 -bor [Net.SecurityProtocolType]::Tls13 } catch {};" ^
      "Invoke-WebRequest -Uri $u -OutFile $p -UseBasicParsing"
    if errorlevel 1 (
      echo ERROR: Failed to download "%DIST_URL%".
      exit /b 1
    )
  )

  echo Extracting Gradle...
  "%PS_EXE%" -NoProfile -ExecutionPolicy Bypass -Command ^
    "$zip='%ZIP_PATH%'; $dst='%BOOTSTRAP_DIR%';" ^
    "if (Test-Path $dst) { } else { New-Item -ItemType Directory -Force -Path $dst | Out-Null };" ^
    "Expand-Archive -LiteralPath $zip -DestinationPath $dst -Force"
  if errorlevel 1 (
    echo ERROR: Failed to extract "%ZIP_PATH%".
    exit /b 1
  )
)

call "%GRADLE_BIN%" %*
exit /b %errorlevel%

