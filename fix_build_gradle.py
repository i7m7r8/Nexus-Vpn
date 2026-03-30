#!/usr/bin/env python3
import sys
from pathlib import Path

path = Path("android/app/build.gradle")
if not path.exists():
    print(f"Error: {path} not found", file=sys.stderr)
    sys.exit(1)

# Rewrite the entire file cleanly
new_content = '''\
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
}

android {
    namespace 'com.nexusvpn.android'
    compileSdk 35

    defaultConfig {
        applicationId 'com.nexusvpn.android'
        minSdk 24
        targetSdk 35
        versionCode 1
        versionName '1.0.0'
    }

    buildTypes {
        release {
            minifyEnabled true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_11
        targetCompatibility JavaVersion.VERSION_11
    }

    kotlin {
        jvmToolchain(11)
    }

    buildFeatures {
        compose true
    }

    composeOptions {
        kotlinCompilerExtensionVersion '1.7.0'
    }

    sourceSets {
        main {
            jniLibs.srcDirs = ['src/main/jniLibs']
        }
    }

    packaging {
        jniLibs {
            pickFirsts += ['lib/**/libnexus_vpn_core.so']
            useLegacyPackaging = false
        }
    }
}

dependencies {
    implementation 'androidx.core:core-ktx:1.15.0'
    implementation 'androidx.lifecycle:lifecycle-runtime-ktx:2.9.0'
    implementation 'androidx.activity:activity-compose:1.10.0'
    implementation platform('androidx.compose:compose-bom:2025.12.00')
    implementation 'androidx.compose.ui:ui'
    implementation 'androidx.compose.ui:ui-graphics'
    implementation 'androidx.compose.ui:ui-tooling-preview'
    implementation 'androidx.compose.material3:material3'
}
'''

path.write_text(new_content)
print("✅ build.gradle rewritten")
