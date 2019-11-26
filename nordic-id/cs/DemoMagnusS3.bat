@echo off
if not exist ".\build" mkdir .\build
copy lib\NurApiDotNet.dll build\NurApiDotNet.dll
copy lib\NURAPI.dll build\NURAPI.dll
C:\Windows\Microsoft.NET\Framework\v2.0.50727\csc /lib:lib /r:NurApiDotNet.dll /out:build\MagnusS3.exe src\Common.cs src\MagnusS3.cs 
if %ERRORLEVEL% EQU 0 (
    REM cls
    build\MagnusS3
)
pause
