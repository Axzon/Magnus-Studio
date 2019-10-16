echo off
cls
if not exist ".\build" mkdir .\build
javac -cp .;./lib/* -d ./build src/thingmagic_samples/Common.java src/thingmagic_samples/MagnusS2.java
if %ERRORLEVEL% EQU 0 (
    java -cp ./lib/*;./build thingmagic_samples.MagnusS2
)
pause
