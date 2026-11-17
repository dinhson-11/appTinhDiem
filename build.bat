@echo off
rem Build giao dien React roi dong goi thanh mot file jar: backend\target\tinhdiem.jar
cd /d "%~dp0"

echo === Build giao dien (React) ===
cd frontend
call npm install
if errorlevel 1 goto :fail
call npm run build
if errorlevel 1 goto :fail

echo === Build may chu (Spring Boot) ===
cd ..\backend
call .\mvnw.cmd -q package
if errorlevel 1 goto :fail

echo.
echo Xong. Chay bang start.bat
cd ..
exit /b 0

:fail
echo.
echo Build that bai.
cd /d "%~dp0"
exit /b 1
