#!/bin/bash

echo "Creating and moving to temp directory"
mkdir temp
cd temp

echo "Installing Babashka"
curl -sLO https://raw.githubusercontent.com/babashka/babashka/master/install
chmod +x install
./install --dir ~/bin

echo "Installing bootleg"
curl -LO https://github.com/retrogradeorbit/bootleg/releases/download/v0.1.9/bootleg-0.1.9-linux-amd64.tgz
tar xvf bootleg-0.1.9-linux-amd64.tgz
mv bootleg ~/bin

echo "Returning to root and running build task"
cd ..
bb build

