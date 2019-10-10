echo off
cls
javac -cp .;./lib/* -d ./build/classes src/Common.java src/MagnusS2.java
if %ERRORLEVEL% EQU 0 (
    cd src
    java -cp .;../lib/*;../build/classes MagnusS2
    cd ..
)
pause