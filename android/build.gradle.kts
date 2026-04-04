// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
}

allprojects {
    repositories {
        google()
        mavenCentral()
        // Guardian Project Maven for tor-android
        maven { url = uri("https://raw.githubusercontent.com/guardianproject/gpmaven/master") }
    }
}
