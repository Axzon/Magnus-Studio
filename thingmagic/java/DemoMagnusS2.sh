#!/bin/bash
@echo off
clear
if [ -d "./build" ]; then
    mkdir -p ./build
fi
javac -cp .:./lib/* -d ./build src/thingmagic_samples/Common.java src/thingmagic_samples/MagnusS2.java
if [ $? -eq 0 ]; then
    java -cp ./lib/*:./build thingmagic_samples.MagnusS2
fi
read -p "Press any key to continue . . ."
