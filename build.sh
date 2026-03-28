#!/bin/bash
# Nexus VPN - Complete Build Script
# Compatible with Termux and Linux

set -e

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_JNI_LIBS="$PROJECT_ROOT/android/app/src/main/jniLibs"

echo "🚀 NEXUS VPN - Build System"
echo "=============================="
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Check Rust installation
if ! command -v cargo &> /dev/null; then
    echo -e "${RED}❌ Rust not found. Install from https://rustup.rs${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Rust found: $(rustup --version)${NC}"

# Ensure Android targets
echo -e "${BLUE}📦 Setting up Rust Android targets...${NC}"
rustup target add aarch64-linux-android x86_64-linux-android 2>/dev/null || true

# Install cargo-ndk
if ! command -v cargo-ndk &> /dev/null; then
    echo -e "${BLUE}📦 Installing cargo-ndk...${NC}"
    cargo install cargo-ndk --quiet
fi

# Build Rust libraries
echo -e "${BLUE}🔨 Building Rust native libraries...${NC}"
cd "$PROJECT_ROOT"

cargo ndk \
    -t arm64-v8a \
    -t x86_64 \
    -o "$ANDROID_JNI_LIBS" \
    build --release 2>&1 | grep -v "Compiling" || true

echo -e "${GREEN}✅ Rust compilation complete${NC}"

# Build Android APKs
echo -e "${BLUE}🔨 Building Android APKs...${NC}"
cd "$PROJECT_ROOT/android"

if [ -f "./gradlew" ]; then
    chmod +x ./gradlew
    ./gradlew assembleRelease
elif command -v gradle &> /dev/null; then
    gradle assembleRelease
else
    echo -e "${RED}❌ Gradle not found. Install Android Studio.${NC}"
    exit 1
fi

echo ""
echo -e "${GREEN}✅ Build Complete!${NC}"
echo ""
echo -e "${YELLOW}📁 Output APKs:${NC}"
ls -lh "$PROJECT_ROOT/android/app/build/outputs/apk/release/"*.apk 2>/dev/null || echo "No APKs found"

echo ""
echo -e "${BLUE}📝 Next steps:${NC}"
echo "  1. adb install android/app/build/outputs/apk/release/app-release.apk"
echo "  2. Or upload to GitHub Releases"
echo ""
