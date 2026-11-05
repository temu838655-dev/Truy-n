plugins {
    id("com.android.application")
}

val ciBuildNumber = providers.environmentVariable("GITHUB_RUN_NUMBER")
    .orElse("1")
    .get()
    .toInt()

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

android {
    namespace = "com.bokaliai.terminal"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bokaliai.terminal"
        minSdk = 23
        targetSdk = 35
        versionCode = ciBuildNumber
        versionName = "0.1.$ciBuildNumber"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
