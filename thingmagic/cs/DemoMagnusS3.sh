#!/bin/bash
@echo off
clear
if [ -d "./build" ]; then
    mkdir -p ./build
fi
cp lib/MercuryAPI.dll build/MercuryAPI.dll
cp lib/ZeroconfService.dll build/ZeroconfService.dll
cp lib/LLRP.dll build/LLRP.dll
csc -lib:'lib' -r:'MercuryAPI.dll,ZeroconfService.dll' -out:'build/MagnusS3.exe' ./src/Common.cs ./src/MagnusS3.cs
if [ $? -eq 0 ]; then
    mono ./build/MagnusS3.exe
fi
read -p "Press any key to continue . . ."
