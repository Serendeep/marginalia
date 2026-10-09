#!/bin/sh
# Exports the release-signing variables the Gradle build reads, taking the
# passwords from the macOS login Keychain so they never sit on disk.
#   source scripts/signing-env.sh && ./gradlew :app:assembleRelease
# One-time setup:
#   security add-generic-password -U -s marginalia-signing -a store-password -w
#   security add-generic-password -U -s marginalia-signing -a key-password -w
#   security add-generic-password -U -s marginalia-signing -a key-alias -w
export SIGNING_KEYSTORE_PATH="${SIGNING_KEYSTORE_PATH:-$HOME/.android/marginalia-release.jks}"
export SIGNING_STORE_PASSWORD="$(security find-generic-password -s marginalia-signing -a store-password -w)"
export SIGNING_KEY_PASSWORD="$(security find-generic-password -s marginalia-signing -a key-password -w)"
export SIGNING_KEY_ALIAS="$(security find-generic-password -s marginalia-signing -a key-alias -w)"
