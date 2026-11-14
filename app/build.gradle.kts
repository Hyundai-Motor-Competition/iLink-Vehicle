plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.ilink.vehicle"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ilink.vehicle"
        // 공모전 기준 minSdk 28 이상. Pleos Connect 에뮬레이터는 API 34.
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "API_BASE_URL", "\"https://ilink-api-dev.chisanw.com\"")
        // true면 서버·Pleos SDK 대신 가짜(DemoWorld)로 돈다. demo 빌드 타입에서만 켠다
        buildConfigField("Boolean", "DEMO", "false")
    }

    buildTypes {
        // 서버 · Pleos 에뮬레이터 없이 일반 에뮬레이터/태블릿에서 흐름을 확인하는 빌드
        //   ./gradlew installDemo
        create("demo") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".demo"
            versionNameSuffix = "-demo"
            buildConfigField("Boolean", "DEMO", "true")
            matchingFallbacks += "debug"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // CarUxRestrictionsManager (주행 규제)
    useLibrary("android.car")
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                // 화면 캡처(PNG)는 -Pscreens 를 줄 때만 돈다. 기본 testDebugUnitTest는 빠르게 유지
                //   scripts/capture-screens.sh  →  screenshots/{landscape,portrait}/*.png
                it.systemProperty("screens.out", rootProject.file("screenshots").absolutePath)
                if (!project.hasProperty("screens")) it.exclude("**/screens/**")
            }
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.pleos.vehicle)
    implementation(libs.pleos.navi.helper)
    implementation(libs.pleos.fused.location)
    implementation(libs.pleos.tts)
    implementation(libs.pleos.stt)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    // V02 오른쪽 작은 지도 (OpenStreetMap, 키 없이 사용)
    implementation(libs.osmdroid)
    // V00 노란 카드의 차 · 깃발 애니메이션 (모바일 스플래시와 같은 Lottie)
    implementation(libs.lottie.compose)
    implementation(libs.okhttp.sse)
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // 화면 캡처: 에뮬레이터 없이 JVM(Robolectric)에서 Compose 화면을 PNG로 저장
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
