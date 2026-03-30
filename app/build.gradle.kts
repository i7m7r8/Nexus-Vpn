plugins {
    id("com.android.application")
    id("kotlin-android")
}

android {
    compileSdk = 34
    namespace = "com.nexus.vpn"
    
    defaultConfig {
        applicationId = "com.nexus.vpn"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.google.android.material:material:1.9.0")
}

tasks.register("downloadTorBinary") {
    doLast {
        val torDir = file("src/main/assets/tor")
        torDir.mkdirs()
        val url = "https://github.com/guardianproject/tor-android-binary/releases/download/tor-0.4.7.13/tor-arm64-v8a"
        val torFile = file("$torDir/tor")
        println("Downloading Tor binary...")
        try {
            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.addRequestProperty("User-Agent", "Mozilla/5.0")
            connection.inputStream.use { input ->
                torFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            torFile.setExecutable(true)
            println("✓ Tor binary ready at: ${torFile.absolutePath}")
        } catch (e: Exception) {
            println("✗ Failed to download Tor: ${e.message}")
            throw e
        }
    }
}

tasks.preBuild.dependsOn("downloadTorBinary")
