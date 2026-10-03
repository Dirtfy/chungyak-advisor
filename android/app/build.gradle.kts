import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// 앱 버전. versionCode는 여기서 계산(major*10000 + minor*100 + patch) → 릴리스마다 단조 증가.
val appVersion = "0.5.0"
val appVersionCode = appVersion.split(".").map { it.toInt() }.let { (a, b, c) -> a * 10000 + b * 100 + c }

// 고정 서명 키: 저장소 밖(../../signing)에 영속 보관, 절대 커밋 금지.
// 빌드마다 새로 생기는 디버그 키로 서명하면 덮어쓰기 업데이트가 실패하므로 모든 빌드를 이 키로 서명.
// 다른 위치면 CHUNGYAK_SIGNING_PROPS=<경로>/keystore.properties 로 지정.
val signingProps = (System.getenv("CHUNGYAK_SIGNING_PROPS")?.let(::File)
    ?: rootProject.file("../../signing/keystore.properties"))
    .takeIf { it.isFile }
    ?.let { f -> Properties().apply { f.inputStream().use(::load) } to f.parentFile }

android {
    namespace = "com.chungyak.advisor"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.chungyak.advisor"
        minSdk = 26
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersion
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        signingProps?.let { (p, dir) ->
            create("fixed") {
                storeFile = File(dir, p.getProperty("storeFile"))
                storePassword = p.getProperty("storePassword")
                keyAlias = p.getProperty("keyAlias")
                keyPassword = p.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        signingConfigs.findByName("fixed")?.let { fixed ->
            getByName("debug").signingConfig = fixed
            getByName("release").signingConfig = fixed
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

// Room 스키마 JSON을 app/schemas/에 export(커밋) — 버전별 스키마 기록과 마이그레이션 검증용.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")

    // Jetpack Compose (versions from BOM)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Room — on-device storage of collected 공고 (nothing leaves the device
    // except the calls to the public data.go.kr API).
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // WorkManager — periodic background polling of the 청약홈 API.
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    // 업데이트 시 데이터 보존 검증(실제 Room 마이그레이션·백업 코드를 JVM에서 실행).
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core-ktx:1.6.1")

}
