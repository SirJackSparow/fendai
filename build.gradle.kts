plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("maven-publish")
}

android {
    namespace = "com.dg.fendai"
    compileSdk = 36

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    kotlinOptions {
        jvmTarget = "11"
    }

    publishing {
        singleVariant("release")
    }
}

publishing {
    publications {
        create<MavenPublication>("release") {
            groupId = "com.fendai"     // group for Maven coordinate
            artifactId = "fendai"     // artifact name
            version = "1.0.1"            // version tag

            afterEvaluate {
                from(components["release"]) // publish release AAR
            }
        }
    }

    repositories {
        maven {
            name = "FendaiLib"
            url = uri("https://maven.pkg.github.com/SirJackSparow/fendai")

            credentials {
                username = project.findProperty("gpr.user") as String?
                    ?: System.getenv("USERNAME_GITHUB")
                password = project.findProperty("gpr.key") as String?
                    ?: System.getenv("TOKEN_GITHUB")
            }
        }
    }
}


dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.kotlin.reflect) // Or your Kotlin version
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}