#!/usr/bin/env python3
import sys
from pathlib import Path

# ─── Root build.gradle ───────────────────────────────────────────
root_gradle = Path("android/build.gradle")
root_content = '''\
// Top-level build file
plugins {
    id 'com.android.application' version '9.0.0' apply false
    id 'org.jetbrains.kotlin.plugin.compose' version '2.0.21' apply false
}

task clean(type: Delete) {
    delete rootProject.buildDir
}
'''
root_gradle.write_text(root_content)
print("✅ Root build.gradle: removed kotlin.android plugin")

# ─── App build.gradle ────────────────────────────────────────────
app_gradle = Path("android/app/build.gradle")
app_content = '''\
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.plugin.compose'
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

    buildFeatures {
        compose true
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
app_gradle.write_text(app_content)
print("✅ App build.gradle: only compose plugin, no kotlin.android")
