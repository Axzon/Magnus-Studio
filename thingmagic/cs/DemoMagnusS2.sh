#!/bin/bash
clear
mkdir -p ./build
cp lib/MercuryAPI.dll build/MercuryAPI.dll
cp lib/ZeroconfService.dll build/ZeroconfService.dll
cp lib/LLRP.dll build/LLRP.dll
csc -lib:'lib' -r:'MercuryAPI.dll,ZeroconfService.dll' -out:'build/MagnusS2.exe' ./src/Common.cs ./src/MagnusS2.cs
if [ $? -eq 0 ]; then
    mono ./build/MagnusS2.exe
fi
read -p "Press any key to continue . . ."
