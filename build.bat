@echo off
setlocal
title Rebuild ValhallaMMO (Paper 26.1)

REM ============================================================
REM  Configuracao (edite aqui se trocar de JDK)
REM ============================================================
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot"
set "PROJECT=%~dp0"
set "MVN=%PROJECT%build-tools\maven\apache-maven-3.9.9\bin\mvn.cmd"
set "MODULES=core,v1_19_R1,v1_20_R4,v1_21_R1,v1_21_R2,v26_R1,dist"
REM ============================================================

echo ============================================
echo   Rebuild ValhallaMMO 1.9.3 (Paper 26.1)
echo ============================================
echo JAVA_HOME = %JAVA_HOME%
echo.

if not exist "%JAVA_HOME%\bin\java.exe" (
    echo *** ERRO: JDK nao encontrado em "%JAVA_HOME%"
    echo Edite a variavel JAVA_HOME no topo deste .bat
    pause
    exit /b 1
)
if not exist "%MVN%" (
    echo *** ERRO: Maven nao encontrado em "%MVN%"
    pause
    exit /b 1
)

cd /d "%PROJECT%"

REM Limpa builds anteriores (evita reaproveitar artefatos defasados no jar final)
for %%m in (core v1_19_R1 v1_20_R4 v1_21_R1 v1_21_R2 v26_R1 dist) do if exist "%PROJECT%%%m\target" rmdir /S /Q "%PROJECT%%%m\target"
if exist "%PROJECT%target" rmdir /S /Q "%PROJECT%target"

echo Compilando os 6 modulos da 26.1...
echo.
call "%MVN%" -pl %MODULES% -o -DskipTests package
if errorlevel 1 (
    echo.
    echo *** BUILD FALHOU - veja os erros acima ***
    pause
    exit /b 1
)

copy /Y "%PROJECT%dist\target\ValhallaMMO_1.9.3.jar" "%PROJECT%ValhallaMMO_1.9.3.jar" >nul

echo.
echo ============================================
echo   BUILD OK!
echo   Jar gerado: %PROJECT%ValhallaMMO_1.9.3.jar
echo ============================================
echo Copie esse jar para a pasta plugins do servidor e reinicie.
echo.
pause
