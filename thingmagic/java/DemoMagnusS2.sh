#!/bin/bash
mkdir -p ./build/classes
javac -cp .:./lib/* -d ./build/classes src/Common.java src/MagnusS2.java
java -cp ./lib/*:./build/classes thingmagic.java.src.MagnusS2
