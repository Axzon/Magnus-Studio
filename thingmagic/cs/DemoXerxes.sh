#!/bin/bash
mkdir -p ./build

cp lib/MercuryAPI.dll build/MercuryAPI.dll
cp lib/ZeroconfService.dll build/ZeroconfService.dll
cp lib/LLRP.dll build/LLRP.dll

csc -lib:'lib' -r:'MercuryAPI.dll,ZeroconfService.dll' -out:'build/Xerxes.exe' src/Common.cs src/Xerxes.cs
mono ./build/Xerxes.exe

