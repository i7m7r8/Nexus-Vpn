#!/bin/bash
# NEXUS VPN - Termux Quick Start Guide
# Run this to get up and running in 5 minutes

echo "🚀 NEXUS VPN - Termux Setup"
echo "============================"
echo ""

# Step 1: Update packages
echo "📦 Updating packages..."
pkg update -y && pkg upgrade -y

# Step 2: Install dependencies
echo "📥 Installing dependencies..."
pkg install -y rust cargo clang openssl-dev git

# Step 3: Setup Rust Android targets
echo "🎯 Setting up Rust for Android..."
rustup target add aarch64-linux-android x86_64-linux-android
cargo install cargo-ndk

# Step 4: Clone repository
echo "📥 Cloning Nexus VPN repository..."
git clone https://github.com/YOUR_USERNAME/nexus-vpn.git
cd nexus-vpn

# Step 5: Configure git
echo "🔧 Configuring git..."
git config user.name "Your Name"
git config user.email "your@email.com"

# Step 6: Build
echo "🔨 Building APKs (this may take 5-10 minutes)..."
chmod +x build.sh
./build.sh

echo ""
echo "✅ Done! APKs ready:"
echo ""
ls -lh android/app/build/outputs/apk/release/*.apk

echo ""
echo "📱 Install on device:"
echo "  adb install android/app/build/outputs/apk/release/app-release.apk"
echo ""
echo "📤 Push to GitHub:"
echo "  git add ."
echo "  git commit -m \"Build: Fresh APK\""
echo "  git push"
echo ""
