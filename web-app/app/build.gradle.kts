plugins {
  id("com.android.application")
}

android {
  namespace = "com.northernfashions.nfllive.web"
  compileSdk { version = release(36) }

  defaultConfig {
    // A separate app id, so it never clashes with the existing NFL LIVE Android app.
    applicationId = "com.northernfashions.nfllive.web"
    minSdk = 24
    targetSdk = 36
    versionCode = 3
    versionName = "1.2"
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      // Signed with the build machine's debug key: fine for in-house installs.
      signingConfig = signingConfigs.getByName("debug")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
}

dependencies {
  implementation("androidx.webkit:webkit:1.12.1")
}
