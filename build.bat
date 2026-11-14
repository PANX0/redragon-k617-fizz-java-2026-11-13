@echo off
REM ================================================================
REM  build.bat — Compila el controlador y genera RedragonK617.exe
REM  Requisitos: JDK 17+ y Maven instalados
REM  Ejecutar como Administrador (PowerShell o CMD)
REM ================================================================

echo.
echo [1/3] Compilando y generando fat JAR...
call mvn clean package -q
if errorlevel 1 (
    echo [ERROR] Fallo en mvn package. Verifica que Maven y JDK esten instalados.
    pause
    exit /b 1
)
echo       OK — target\redragon-k617-1.0.0.jar generado

echo.
echo [2/3] Creando .exe con jpackage...
if exist dist rmdir /s /q dist
jpackage ^
  --input target ^
  --main-jar redragon-k617-1.0.0.jar ^
  --main-class com.redragon.Main ^
  --name RedragonK617 ^
  --app-version 1.0.0 ^
  --description "Redragon K617 Fizz RGB Controller" ^
  --dest dist ^
  --type app-image ^
  --win-console
if errorlevel 1 (
    echo [ERROR] Fallo jpackage. Verifica que tengas JDK 17 o superior.
    pause
    exit /b 1
)
echo       OK — dist\RedragonK617\RedragonK617.exe generado

echo.
echo [3/3] Listo!
echo.
echo Para usar el .exe desde cualquier carpeta, agrega a tu PATH:
echo   dist\RedragonK617\
echo.
echo O ejecuta directamente:
echo   dist\RedragonK617\RedragonK617.exe demo colors
echo.
pause
