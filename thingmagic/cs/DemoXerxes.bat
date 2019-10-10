echo off
if not exist ".\build" mkdir .\build
copy lib\MercuryAPI.dll build\MercuryAPI.dll
copy lib\ZeroconfService.dll build\ZeroconfService.dll
cls
csc /lib:lib /r:MercuryAPI.dll,ZeroconfService.dll /out:build\Xerxes.exe src\Common.cs src\Xerxes.cs 
if %ERRORLEVEL% EQU 0 (
    build\Xerxes
)
pause