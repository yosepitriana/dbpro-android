plugins {
    id("com.android.application")
}

android {
    namespace = "id.dbpro.central.monitor"
    compileSdk = 35
    defaultConfig {
        applicationId = "id.dbpro.central.monitor"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "1.0.5"
        buildConfigField("String", "API_BASE_URL", "\"https://central-app.dbpro.id/api/v1/\"")
    }
    buildFeatures { buildConfig = true }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}
