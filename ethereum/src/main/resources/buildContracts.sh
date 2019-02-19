#!/bin/sh

solc contracts/*.sol --abi --bin --optimize -o ./build
