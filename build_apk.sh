#!/bin/bash
set -e

echo "======================================================"
echo "    PANDA TOUCH PRO (ROOT) - APK BUILD SCRIPT"
echo "======================================================"

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

if [ ! -f "gradlew" ]; then
    echo "[!] Error: gradlew not found in $PROJECT_DIR"
    exit 1
fi

chmod +x gradlew

# Build Debug APK (gồm cả C++ NDK shared libraries & AIDL code generation)
echo "[+] Compiling C++ NDK (CMake) + AIDL + Kotlin App..."
./gradlew assembleDebug

DEBUG_APK="app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$DEBUG_APK" ]; then
    echo ""
    echo "[✓] Build hoàn tất thành công!"
    echo "[✓] File APK đầu ra: $PROJECT_DIR/$DEBUG_APK"
    echo ""
    echo "Lệnh cài đặt trực tiếp vào thiết bị Android:"
    echo "  adb install -r $DEBUG_APK"
    echo "Hoặc qua Shizuku trên máy:"
    echo "  rish -c \"pm install -r $DEBUG_APK\""
else
    echo "[!] Build kết thúc nhưng không tìm thấy file APK tại vị trí chuẩn."
fi
