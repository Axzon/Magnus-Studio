#!/bin/bash
clear
mkdir -p ./build
javac -cp .:./lib/* -d ./build src/thingmagic_samples/Common.java src/thingmagic_samples/Xerxes.java
if [ $? -eq 0 ]; then
    java -cp ./lib/*:./build thingmagic_samples.Xerxes
fi
read -p "Press any key to continue . . ."
