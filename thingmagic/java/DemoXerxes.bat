echo off
cls
javac -cp .;./lib/* -d ./build/classes src/Common.java src/Xerxes.java
if %ERRORLEVEL% EQU 0 (
    cd src
    java -cp .;../lib/*;../build/classes Xerxes
    cd ..
)
pause