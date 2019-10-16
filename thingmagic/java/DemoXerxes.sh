#!/bin/bash
mkdir -p ./build/classes
javac -cp .:./lib/* -d ./build/classes src/Common.java src/Xerxes.java
java -cp ./lib/*:./build/classes thingmagic.java.src.Xerxes

