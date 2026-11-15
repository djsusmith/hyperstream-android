#!/data/user/0/com.agy/files/usr/bin/bash
# Runs Gradle on-device (Android/aarch64): disables Gradle native services (libnative-platform.so
# is unavailable on Android), uses the local JDK 21 trust store and the native aapt2 binary.
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
PREFIX_DIR="${PREFIX:-/data/user/0/com.agy/files/usr}"
export TMPDIR="$PREFIX_DIR/tmp"
export TMP="$PREFIX_DIR/tmp"
export TEMP="$PREFIX_DIR/tmp"
export JAVA_HOME="$PREFIX_DIR/lib/jvm/java-21-openjdk"
export PATH="$JAVA_HOME/bin:$PATH"
mkdir -p "$PREFIX_DIR/tmp"
JVM_FLAGS="-Xmx3g -Djava.io.tmpdir=$PREFIX_DIR/tmp -Dorg.gradle.native=false -Djavax.net.ssl.trustStore=$JAVA_HOME/lib/security/cacerts -Djavax.net.ssl.trustStorePassword=changeit"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-${HOME:-/data/user/0/com.agy/files}/.gradle}"
export GRADLE_OPTS="-Dorg.gradle.native=false -Djava.io.tmpdir=$PREFIX_DIR/tmp"
exec gradle -Dorg.gradle.jvmargs="$JVM_FLAGS" -Dkotlin.daemon.jvm.options="-Djava.io.tmpdir=$PREFIX_DIR/tmp" -Pandroid.aapt2FromMavenOverride="$PREFIX_DIR/bin/aapt2" -p "$ROOT_DIR/android" "$@"
