#!/bin/bash

echo "Creating and moving to temp directory"
rm -rf temp
mkdir temp
cd temp
export PATH=$PATH:$(pwd)

echo "Installing Babashka"
curl -sLO https://raw.githubusercontent.com/babashka/babashka/master/install
chmod +x install
./install --dir .

echo "Installing bootleg"
curl -LO https://github.com/retrogradeorbit/bootleg/releases/download/v0.1.9/bootleg-0.1.9-linux-amd64.tgz
tar xvf bootleg-0.1.9-linux-amd64.tgz

echo "Returning to root and running build task"
cd ..
bb build

