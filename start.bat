@echo off
rem Chay ung dung tinh diem chi bang 1 cu nhap dup: tu build neu chua co, tu mo trinh duyet, roi chay may chu.
rem Du lieu luu o data\games.json. Dong cua so nay de tat ung dung.
cd /d "%~dp0"
chcp 65001 >nul
title Tinh diem

if not exist backend\target\tinhdiem.jar (
  echo Lan dau chay: dang build, vui long doi...
  call .\build.bat
  if errorlevel 1 (
    echo.
    echo Build that bai.
    pause
    exit /b 1
  )
)

rem Mo trinh duyet sau vai giay, luc may chu da san sang
start "" /b cmd /c "ping -n 6 127.0.0.1 >nul & start http://localhost:8080"

java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -jar backend\target\tinhdiem.jar
pause
