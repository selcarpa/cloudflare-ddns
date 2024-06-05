#!/bin/bash

os=$TARGETOS
arch=$TARGETARCH

# convert arch to graalvm supported arch
case $arch in
    amd64)
        arch="x64"
        ;;
    arm64)
        arch="aarch64"
        ;;
    *)
        echo "Unsupported architecture: $arch"
        exit 1
        ;;
esac

# install necessary packages
apt-get update
apt-get install build-essential zlib1g-dev curl -y

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

cd cloudflare-ddns/graalvm-build

./gradlew nativeCompile

