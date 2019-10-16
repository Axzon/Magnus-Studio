#!/bin/bash
mkdir -p ./build

cp lib/MercuryAPI.dll build/MercuryAPI.dll
cp lib/ZeroconfService.dll build/ZeroconfService.dll
cp lib/LLRP.dll build/LLRP.dll

csc -lib:'lib' -r:'MercuryAPI.dll,ZeroconfService.dll' -out:'build/MagnusS2.exe' src/Common.cs src/MagnusS2.cs
mono ./build/MagnusS2.exe

