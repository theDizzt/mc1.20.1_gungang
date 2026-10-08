@echo off
setlocal EnableExtensions DisableDelayedExpansion
title Gungang Acoustics - Export JAR
set "RESULT=1"
set "BUILD_JAVA_HOME="
pushd "%~dp0"
if errorlevel 1 exit /b 1

if defined JDK17_HOME call :checkJava "%JDK17_HOME%"
if not defined BUILD_JAVA_HOME if defined JAVA_HOME call :checkJava "%JAVA_HOME%"
if not defined BUILD_JAVA_HOME call :checkJava "%ProgramFiles%\Java\jdk-17"
if not defined BUILD_JAVA_HOME for /d %%J in ("%ProgramFiles%\Java\jdk-17*" "%ProgramFiles%\Eclipse Adoptium\jdk-17*" "%USERPROFILE%\.jdks\*17*") do call :checkJava "%%~fJ"
if not defined BUILD_JAVA_HOME goto :noJava

set "JAVA_HOME=%BUILD_JAVA_HOME%"
echo Using Java 17: "%JAVA_HOME%"
echo Building and testing the mod. The first build needs internet access.
echo.
call "%~dp0gradlew.bat" exportJar --console=plain
if errorlevel 1 goto :buildFailed

set "RESULT=0"
echo.
echo SUCCESS: The installable JAR has been exported to:
echo "%~dp0dist"
echo Put the JAR in your Minecraft client's mods folder.
goto :finish

:noJava
echo ERROR: Java 17 JDK was not found.
echo Install a Java 17 JDK or set JDK17_HOME to its installation folder.
goto :finish

:buildFailed
echo.
echo ERROR: Build or export failed. See the error messages above.
echo Existing files in dist may be from an earlier successful build.
goto :finish

:finish
popd
if /i not "%~1"=="--no-pause" pause
endlocal & exit /b %RESULT%

:checkJava
if defined BUILD_JAVA_HOME exit /b
if not exist "%~1\bin\java.exe" exit /b
if not exist "%~1\bin\javac.exe" exit /b
if not exist "%~1\release" exit /b
set "DETECTED_JAVA_VERSION="
for /f "usebackq tokens=1,* delims==" %%A in ("%~1\release") do if "%%A"=="JAVA_VERSION" set "DETECTED_JAVA_VERSION=%%~B"
if "%DETECTED_JAVA_VERSION:~0,3%"=="17." set "BUILD_JAVA_HOME=%~1"
exit /b
