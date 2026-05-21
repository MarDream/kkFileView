@echo off
set "KKFILEVIEW_BIN_FOLDER=%cd%"
cd "%KKFILEVIEW_BIN_FOLDER%"
if not defined LOG_PATH set "LOG_PATH=..\log"
if not exist "%LOG_PATH%" mkdir "%LOG_PATH%"
set "JAR_NAME="
for %%F in (kkFileView-*.jar) do (
    set "JAR_NAME=%%~nxF"
    goto :jar_found
)
echo Error: kkFileView jar not found in %KKFILEVIEW_BIN_FOLDER%
exit /b 1

:jar_found
echo Using KKFILEVIEW_BIN_FOLDER %KKFILEVIEW_BIN_FOLDER%
echo Using JAR_NAME %JAR_NAME%
echo Using LOG_PATH %LOG_PATH%
echo Starting kkFileView...
echo Please check log files in %LOG_PATH% for more information
echo You can get help in our official home site: https://kkview.cn
echo If you need further help, please join our kk opensource community: https://t.zsxq.com/09ZHSXbsQ
echo If this project is helpful to you, please star it on https://gitee.com/kekingcn/file-online-preview/stargazers
java -Dfile.encoding=UTF-8 -Dspring.config.location=..\config\application.properties -jar "%JAR_NAME%"
