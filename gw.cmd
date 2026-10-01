@echo off
rem Gradle, with JAVA_HOME resolved for you.
rem
rem gradlew.bat needs a JVM on PATH or JAVA_HOME before Gradle's toolchain
rem support can do anything. This wrapper points it at the JDKs paper-scaffold
rem installed, so the project builds on a machine with no system-wide Java.
rem
rem A .cmd runs whatever the PowerShell execution policy is, which is why the
rem Windows wrapper is one.
rem
rem Use gradlew.bat directly instead if Gradle finds every JDK the build asks
rem for by itself: Java 25 or newer to run Gradle on (CLAUDE.md, "Toolchain"),
rem as JAVA_HOME or on PATH, and each line's Java (java_version in
rem gradle.properties) for its modules.
rem
rem No labels or goto: this file can have LF line endings, since git converts
rem them only on checkout, and cmd.exe finds labels unreliably in such a file.

setlocal
rem pushd, not cd, since cmd.exe cannot make a UNC folder such as
rem \\wsl.localhost\... its current folder, and pushd maps it to a drive. Each
rem exit below pops it again, which frees that drive.
pushd "%~dp0"

rem The build JDK's version, read from gradle.properties:
rem the last buildJdk line, blanks dropped.
set "BUILD_JDK="
for /f "usebackq tokens=1,* delims==" %%a in ("gradle.properties") do for /f %%k in ("%%a") do if "%%k"=="buildJdk" (
    set "BUILD_JDK="
    for /f %%v in ("%%b") do set "BUILD_JDK=%%v"
)
if not defined BUILD_JDK (
    >&2 echo gradle.properties has no buildJdk line.
    popd & exit /b 1
)

rem The paper-scaffold home: PAPER_SCAFFOLD_HOME when set, the same default
rem otherwise as the generator's.
set "SCAFFOLD_HOME=%USERPROFILE%\.paper-scaffold"
if defined PAPER_SCAFFOLD_HOME set "SCAFFOLD_HOME=%PAPER_SCAFFOLD_HOME%"

rem Every JDK paper-scaffold installed, for Gradle's toolchains, since Gradle
rem looks in the paper-scaffold home only when told to: the build JDK, which
rem gradle/gradle-daemon-jvm.properties asks for when another release runs the
rem launcher, and each line's Java, which its modules ask for. A marker holds
rem its JDK's home relative to the marker's folder; markers from earlier
rem versions hold an absolute path. CACHED is the build JDK's home.
rem call expands a line a second time as it runs, which would drop a % from a
rem path written into it; %%JDK%% puts the path in at that point instead.
set "CACHED="
set "HOMES="
for /d %%j in ("%SCAFFOLD_HOME%\jdks\temurin-*") do if exist "%%j\.complete" for /f "usebackq delims=" %%m in ("%%j\.complete") do (
    set "JDK="
    if exist "%%j\%%m\bin\java.exe" (set "JDK=%%j\%%m") else if exist "%%m\bin\java.exe" set "JDK=%%m"
    if defined JDK call set "HOMES=%%HOMES%%,%%JDK%%"
    if defined JDK if /i "%%~nxj"=="temurin-%BUILD_JDK%" call set "CACHED=%%JDK%%"
)
set "INSTALLATIONS="
if defined HOMES set INSTALLATIONS="-Dorg.gradle.java.installations.paths=%HOMES:~1%"

rem Quotes around the value are a common way to set it, and break every path
rem built from it below; gradlew.bat strips them the same way.
if defined JAVA_HOME set "JAVA_HOME=%JAVA_HOME:"=%"
set "OWN_JAVA="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "OWN_JAVA=1"

rem Its major version: 11 from "11.0.2", 8 from "1.8.0_392".
set "RELEASE="
if defined OWN_JAVA if exist "%JAVA_HOME%\release" for /f "usebackq tokens=1,2 delims==" %%a in ("%JAVA_HOME%\release") do if "%%a"=="JAVA_VERSION" set "RELEASE=%%~b"
set "MAJOR="
if defined RELEASE for /f "tokens=1,2 delims=._-+" %%m in ("%RELEASE%") do if "%%m"=="1" (set "MAJOR=%%n") else set "MAJOR=%%m"

rem Gradle itself runs on Java 25 or newer here (CLAUDE.md, "Toolchain"), so the
rem cached JDK replaces a JAVA_HOME older than that.
set "TOO_OLD="
for /f %%m in ("%MAJOR%") do if %%m LSS 25 set "TOO_OLD=1"
if defined TOO_OLD if not defined CACHED (
    >&2 echo JAVA_HOME is Java %MAJOR%; this build needs 25 or newer to run Gradle.
    >&2 echo Point JAVA_HOME at a JDK %BUILD_JDK%, from
    >&2 echo   https://adoptium.net/temurin/releases/?version=%BUILD_JDK%
    popd & exit /b 1
)
if defined TOO_OLD set "OWN_JAVA="

rem A JAVA_HOME with no java in it makes gradlew.bat refuse to start, even with
rem java on PATH, so the cached JDK replaces it, as it does one too old, or it
rem is dropped.
if not defined OWN_JAVA set "JAVA_HOME="
if not defined OWN_JAVA if defined CACHED set "JAVA_HOME=%CACHED%"

set "FOUND="
if defined OWN_JAVA set "FOUND=1"
if defined CACHED set "FOUND=1"
if not defined FOUND where java >nul 2>nul && set "FOUND=1"
if not defined FOUND (
    >&2 echo No JDK %BUILD_JDK% found. Install one from
    >&2 echo   https://adoptium.net/temurin/releases/?version=%BUILD_JDK%
    >&2 echo and point JAVA_HOME at it.
    popd & exit /b 1
)

rem The options and arguments go in at call's second expansion too, so a % in
rem them stays. The last line reads %ERRORLEVEL% before its popd runs.
call .\gradlew.bat %%INSTALLATIONS%% %%*
popd & exit /b %ERRORLEVEL%
