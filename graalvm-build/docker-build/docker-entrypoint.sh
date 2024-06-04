#!/bin/bash

# check arguments
if [ "$#" -ne 2 ]; then
    echo "Usage: $0 <os> <arch>"
    exit 1
fi

os=$1
arch=$2

# install necessary packages
sudo apt-get update
sudo apt-get install build-essential zlib1g-dev curl git -y

# graalvm download url
downloadUrl="https://download.oracle.com/graalvm/17/latest/graalvm-jdk-17_${os}-${arch}_bin.tar.gz"

# download graalvm
echo "Downloading GraalVM from $downloadUrl..."
curl -L $downloadUrl -o graalvm.tar.gz

# check download status
if [ $? -ne 0 ]; then
    echo "Download failed!"
    exit 1
fi

# extract graalvm
echo "Extracting GraalVM..."
tar -xvf graalvm.tar.gz

#  check extraction status
rm graalvm.tar.gz

# find graalvm directory
graalvmPath=$(ls -d graalvm-jdk-17*)

# check graalvm directory
if [ -z "$graalvmPath" ]; then
    echo "Extraction failed or GraalVM directory not found!"
    exit 1
fi

# set environment variables
export JAVA_HOME=$PWD/$graalvmPath
export PATH=$JAVA_HOME/bin:$PATH

# check java version
java -version

git clone https://github.com/selcarpa/cloudflare-ddns

cd cloudflare-ddns/graalvm-build

./gradlew nativeCompile

