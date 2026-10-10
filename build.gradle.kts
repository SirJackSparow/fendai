plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("maven-publish")
}

android {
    namespace = "com.dg.fendai"
    compileSdk = 35

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        externalNativeBuild {
            cmake {
                cppFlags += listOf()
                // allow compiling 16 KB page-aligned shared libraries
                // https://developer.android.com/guide/practices/page-sizes#compile-r27
                arguments += listOf("-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON")
                arguments += "-DCMAKE_BUILD_TYPE=Release"

                // (debugging) uncomment the following line to enable debug builds
                // and attach hardware-assisted address sanitizer
                // arguments += "-DCMAKE_BUILD_TYPE=Debug"
                // arguments += listOf("-DANDROID_SANITIZE=hwaddress")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"

        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        debug {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.dg.fendai"     // group for Maven coordinate
            artifactId = "fendai"     // artifact name
            version = "1.0.0-alpha01.0.3rag"            // version tag

            afterEvaluate {
                // Ensure the component is available before attempting to use it
                val releaseComponent = components.findByName("release")
                if (releaseComponent != null) {
                    from(releaseComponent)
                }
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

tasks.register("prepareKotlinBuildScriptModel") {}


dependencies {

    implementation(libs.kotlin.reflect)
    implementation(libs.androidx.startup.runtime) // Or your Kotlin version
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}