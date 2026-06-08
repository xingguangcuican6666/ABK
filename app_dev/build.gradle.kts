plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val githubClientId = providers.gradleProperty("ABK_GITHUB_CLIENT_ID")
    .orElse(providers.environmentVariable("ABK_GITHUB_CLIENT_ID"))
    .orElse("Ov23li8skGo6AFPBeSTh")
val appVersionCode = 10021
val appVersionName = "1.2.1-dev"
val appUpdateMetadataUrl = providers.environmentVariable("ABK_APP_UPDATE_METADATA_URL")
    .orElse("https://raw.githubusercontent.com/xingguangcuican6666/ABK/dev/version.json")
val appBuildTimestamp = providers.environmentVariable("ABK_APP_BUILD_TIMESTAMP")
    .orElse("")
val appBuildTimestampEpochMillis = providers.environmentVariable("ABK_APP_BUILD_TIMESTAMP_EPOCH_MILLIS")
    .map { raw -> raw.toLongOrNull()?.let { "${it}L" } ?: "0L" }
    .orElse("0L")
val appBuildRunId = providers.environmentVariable("ABK_APP_BUILD_RUN_ID")
    .map { raw -> raw.toLongOrNull()?.let { "${it}L" } ?: "0L" }
    .orElse("0L")
val appBuildCommitSha = providers.environmentVariable("ABK_APP_BUILD_COMMIT_SHA")
    .orElse("")
val appBuildWorkflowName = providers.environmentVariable("ABK_APP_BUILD_WORKFLOW_NAME")
    .orElse("")

val releaseStoreFile = providers.gradleProperty("ABK_RELEASE_STORE_FILE")
    .orElse(providers.environmentVariable("ABK_RELEASE_STORE_FILE"))
val releaseStorePassword = providers.gradleProperty("ABK_RELEASE_STORE_PASSWORD")
    .orElse(providers.environmentVariable("ABK_RELEASE_STORE_PASSWORD"))
val releaseStoreType = providers.gradleProperty("ABK_RELEASE_STORE_TYPE")
    .orElse(providers.environmentVariable("ABK_RELEASE_STORE_TYPE"))
    .orElse("JKS")
val releaseKeyAlias = providers.gradleProperty("ABK_RELEASE_KEY_ALIAS")
    .orElse(providers.environmentVariable("ABK_RELEASE_KEY_ALIAS"))
val releaseKeyPassword = providers.gradleProperty("ABK_RELEASE_KEY_PASSWORD")
    .orElse(providers.environmentVariable("ABK_RELEASE_KEY_PASSWORD"))
val hasReleaseSigning = !releaseStoreFile.orNull.isNullOrBlank() &&
    !releaseStorePassword.orNull.isNullOrBlank() &&
    !releaseKeyAlias.orNull.isNullOrBlank() &&
    !releaseKeyPassword.orNull.isNullOrBlank()

android {
    namespace = "com.abk.kernel"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.abk.kernel"
        minSdk = 26
        targetSdk = 35
        versionCode = 10021
        versionName = "1.2.1-dev"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("long", "APP_VERSION_CODE", "${appVersionCode}L")
        buildConfigField("String", "APP_VERSION_NAME", "\"$appVersionName\"")
        buildConfigField("String", "APP_UPDATE_METADATA_URL", "\"${appUpdateMetadataUrl.get()}\"")
        buildConfigField("String", "APP_BUILD_TIMESTAMP", "\"${appBuildTimestamp.get()}\"")
        buildConfigField("long", "APP_BUILD_TIMESTAMP_EPOCH_MILLIS", appBuildTimestampEpochMillis.get())
        buildConfigField("long", "APP_BUILD_RUN_ID", appBuildRunId.get())
        buildConfigField("String", "APP_BUILD_COMMIT_SHA", "\"${appBuildCommitSha.get()}\"")
        buildConfigField("String", "APP_BUILD_WORKFLOW_NAME", "\"${appBuildWorkflowName.get()}\"")
        buildConfigField("String", "GITHUB_CLIENT_ID", "\"${githubClientId.get()}\"")
        buildConfigField("String", "SOURCE_REPO_OWNER", "\"xingguangcuican6666\"")
        buildConfigField("String", "SOURCE_REPO_NAME", "\"ABK\"")
        buildConfigField("String", "SOURCE_REPO_DEFAULT_BRANCH", "\"dev\"")
        buildConfigField("String", "UPSTREAM_REPO_URL", "\"https://github.com/zzh20188/GKI_KernelSU_SUSFS\"")
        buildConfigField("String", "TOP_LEVEL_REPO_URL", "\"https://github.com/WildKernels/GKI_KernelSU_SUSFS\"")

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
            }
        }
    }

    ndkVersion = "28.2.13676358"

    signingConfigs {
        create("release") {
            storeFile = releaseStoreFile.orNull?.let { file(it) }
            storePassword = releaseStorePassword.orNull
            storeType = releaseStoreType.get()
            keyAlias = releaseKeyAlias.orNull
            keyPassword = releaseKeyPassword.orNull
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    sourceSets {
        getByName("main") {
            assets.srcDir("build/generated/abk-assets/main")
            jniLibs.srcDir("build/generated/abk-jniLibs/main")
        }
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.google.material)
    implementation(libs.androidx.navigation.compose)

    // Network
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)
    implementation(libs.kotlinx.serialization.json)

    // Root
    implementation(libs.libsu.core)
    implementation(libs.libsu.io)

    // Image
    implementation(libs.coil.compose)

    // Background work & notifications
    implementation(libs.work.runtime.ktx)

    // Preferences
    implementation(libs.datastore.preferences)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
