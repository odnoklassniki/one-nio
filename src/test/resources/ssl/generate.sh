#!/bin/bash

rm -f ca.kley ca.crt certificate.key certificate.crt trustore.jks

# CA
openssl req -new \
    -days 7300 \
    -nodes \
    -x509 \
    -sha256 \
    -newkey rsa:2048 \
    -keyout ca.key \
    -out ca.crt \
    -subj "/CN=Test CA"

openssl req -new \
    -days 7300 \
    -nodes \
    -x509 \
    -sha256 \
    -newkey rsa:2048 \
    -addext basicConstraints=critical,CA:FALSE \
    -CA ca.crt \
    -CAkey ca.key \
    -keyout certificate.key \
    -out certificate.crt \
    -subj "/CN=Test certificate"

keytool -import -file ca.crt -keystore trustore.jks -storepass changeit -noprompt
