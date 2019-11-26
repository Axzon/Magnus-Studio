#!/bin/bash
@echo off
clear
if [ -d "./build" ]; then
    mkdir -p ./build
fi
javac -cp .:./lib/* -d ./build src/nordicid_samples/Common.java src/nordicid_samples/Xerxes.java
if [ $? -eq 0 ]; then
    java -cp ./lib/*:./build nordicid_samples.Xerxes
fi
read -p "Press any key to continue . . ."
