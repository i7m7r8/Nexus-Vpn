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

// Download Tor binary on gradle sync
tasks.register("downloadTorBinary") {
    doLast {
        val torDir = file("src/main/assets/tor")
        torDir.mkdirs()
        val torUrl = "https://api.github.com/repos/guardianproject/tor-android-binary/releases/latest"
        
        val client = java.net.URL(torUrl).openConnection() as java.net.HttpURLConnection
        client.requestMethod = "GET"
        client.addRequestProperty("User-Agent", "Mozilla/5.0")
        
        val reader = java.io.BufferedReader(java.io.InputStreamReader(client.inputStream))
        val response = reader.readText()
        reader.close()
        
        val latestUrl = response.substringAfter("\"browser_download_url\": \"").substringBefore("\"")
        val torFile = file("$torDir/tor")
        
        println("Downloading Tor from: $latestUrl")
        java.net.URL(latestUrl).openConnection().getInputStream().use { input ->
            torFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        torFile.setExecutable(true)
        println("Tor binary saved to: ${torFile.absolutePath}")
    }
}

tasks.preBuild.dependsOn("downloadTorBinary")
