@echo off
chcp 65001 >nul

set "PROJECT_ROOT=%cd%"
set "JAR_FILE="
for %%F in ("%PROJECT_ROOT%\server\target\kkFileView-*.jar") do (
    set "JAR_FILE=%%~fF"
    goto :jar_found
)

if not defined JAR_FILE (
    echo Error: kkFileView jar not found in server\target\
    echo Please run: mvn -q -DskipTests package
    exit /b 1
)

:jar_found
set "OFFICE_HOME=%PROJECT_ROOT%\server\LibreOfficePortable\App\libreoffice"
set "CONFIG_FILE=%PROJECT_ROOT%\server\src\main\config\application.properties"
set "LOG_DIR=%PROJECT_ROOT%\log"

if not exist "%LOG_DIR%" mkdir "%LOG_DIR%"

echo ============================================
echo   kkFileView Startup
echo ============================================
echo Project Root: %PROJECT_ROOT%
echo Jar File: %JAR_FILE%
echo Office Home: %OFFICE_HOME%
echo Config: %CONFIG_FILE%
echo Log Dir: %LOG_DIR%
echo ============================================

set KK_OFFICE_HOME=%OFFICE_HOME%
set KK_LOG_DIR=%LOG_DIR%

java -Dfile.encoding=UTF-8 ^
     "-Dspring.config.location=%CONFIG_FILE%" ^
     -jar "%JAR_FILE%"
