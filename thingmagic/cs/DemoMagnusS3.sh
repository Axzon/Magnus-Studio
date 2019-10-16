#!/bin/bash
mkdir -p ./build

cp lib/MercuryAPI.dll build/MercuryAPI.dll
cp lib/ZeroconfService.dll build/ZeroconfService.dll
cp lib/LLRP.dll build/LLRP.dll

csc -lib:'lib' -r:'MercuryAPI.dll,ZeroconfService.dll' -out:'build/MagnusS3.exe' src/Common.cs src/MagnusS3.cs
mono ./build/MagnusS3.exe

