echo off
cls
if not exist ".\build\classes" mkdir .\build\classes
javac -cp .;./lib/* -d ./build/classes src/Common.java src/MagnusS2.java
if %ERRORLEVEL% EQU 0 (
    cd src
    java -cp .;../lib/*;../build/classes MagnusS2
    cd ..
)
pause