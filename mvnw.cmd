@echo off
SET MAVEN_VERSION=3.9.6
SET MAVEN_HOME=%USERPROFILE%\.m2\wrapper\apache-maven-%MAVEN_VERSION%
SET MVN_BIN=%MAVEN_HOME%\bin\mvn.cmd

if not exist "%MVN_BIN%" (
    echo [BOOTSTRAP] Maven non trouve. Telechargement de Maven %MAVEN_VERSION%...
    if not exist "%USERPROFILE%\.m2\wrapper" mkdir "%USERPROFILE%\.m2\wrapper"
    curl -L "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/%MAVEN_VERSION%/apache-maven-%MAVEN_VERSION%-bin.zip" -o "%TEMP%\maven.zip"
    echo [BOOTSTRAP] Extraction...
    powershell -Command "Expand-Archive -Path '%TEMP%\maven.zip' -DestinationPath '%USERPROFILE%\.m2\wrapper' -Force"
    del "%TEMP%\maven.zip"
)

"%MVN_BIN%" %*
