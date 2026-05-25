@echo off
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
echo [VisorWifi] Usando JDK embebido de Android Studio: %JAVA_HOME%
call "%~dp0gradlew.bat" %*
