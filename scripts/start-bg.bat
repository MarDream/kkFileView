@echo off
chcp 65001 >nul
set KK_OFFICE_HOME=E:\Project\mygit\kkFileView\server\LibreOfficePortable\App\libreoffice
set KK_LOG_DIR=E:\Project\mygit\kkFileView\log
start "kkFileView" java -Dfile.encoding=UTF-8 -Dspring.config.location=E:\Project\mygit\kkFileView\server\src\main\config\application.properties -jar E:\Project\mygit\kkFileView\server\target\kkFileView-5.0.0.jar