#!/data/data/com.termux/files/usr/bin/bash
set -e

echo "=========================================================="
echo "  Nebula Limbo 26.3 - Automated Termux Builder (Java 25)  "
echo "=========================================================="

# 1. Check if running inside proot Ubuntu/Debian or native Termux
if [ -f /etc/os-release ] && grep -qiE "ubuntu|debian" /etc/os-release; then
    echo "[*] Detected Ubuntu/Debian (proot-distro) environment."
    apt-get update -y
    apt-get install -y wget curl unzip tar git

    # Install Eclipse Temurin JDK 25 (ARM64 / aarch64) if Java 25 is not installed
    if ! java -version 2>&1 | grep -q 'version "25'; then
        echo "[*] Downloading Eclipse Temurin OpenJDK 25 (aarch64)..."
        mkdir -p /opt/jdk25
        curl -L "https://api.adoptium.net/v3/binary/latest/25/ga/linux/aarch64/jdk/hotspot/normal/eclipse" -o /tmp/jdk25.tar.gz
        tar -xzf /tmp/jdk25.tar.gz -C /opt/jdk25 --strip-components=1
        rm -f /tmp/jdk25.tar.gz
        export JAVA_HOME="/opt/jdk25"
        export PATH="$JAVA_HOME/bin:$PATH"
    fi

    # Install Gradle 9.6.0 if not installed
    if ! gradle -v 2>&1 | grep -q "9.6"; then
        echo "[*] Downloading Gradle 9.6.0..."
        curl -L "https://services.gradle.org/distributions/gradle-9.6.0-bin.zip" -o /tmp/gradle.zip
        unzip -q -o /tmp/gradle.zip -d /opt/
        rm -f /tmp/gradle.zip
        export PATH="/opt/gradle-9.6.0/bin:$PATH"
    fi

    echo "[*] Running Gradle Build (RAM-safe mode for mobile)..."
    gradle build --no-daemon -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m"
    echo ""
    echo "[SUCCESS] File .jar berhasil dibuat di folder: build/libs/"
    ls -lh build/libs/*.jar
else
    echo "[*] Detected Native Termux."
    echo "[*] Installing proot-distro & setting up Ubuntu ARM64 for JDK 25..."
    pkg update -y
    pkg install -y proot-distro unzip wget curl git
    proot-distro install ubuntu || true
    PROJECT_DIR="$(pwd)"
    echo "[*] Launching build inside Ubuntu proot..."
    proot-distro login ubuntu -- bash -c "cd '$PROJECT_DIR' && bash build-termux.sh"
fi
