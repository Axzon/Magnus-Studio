echo off
cls
if not exist ".\build" mkdir .\build
javac -cp .;./lib/* -d ./build src/nordicid_samples/Common.java src/nordicid_samples/MagnusS2.java
if %ERRORLEVEL% EQU 0 (
    java -cp ./lib/*;./build nordicid_samples.MagnusS2
)
pause
