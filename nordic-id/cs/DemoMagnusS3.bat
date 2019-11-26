@echo off
if not exist ".\build" mkdir .\build
copy lib\NurApiDotNet.dll build\NurApiDotNet.dll
copy lib\NURAPI.dll build\NURAPI.dll
csc /lib:lib /r:NurApiDotNet.dll /out:build\MagnusS3.exe src\Common.cs src\MagnusS3.cs 
if %ERRORLEVEL% EQU 0 (
    build\MagnusS3
)
pause
