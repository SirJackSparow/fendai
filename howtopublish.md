# 🧩 Fendai Android Library


---

## ⚙️ Step 1: Create `version.properties`

Create this file at the **root** of your project:

```properties
VERSION_NAME=1.0.0


import java.util.Properties

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
}

// ✅ Load version from version.properties
val versionPropsFile = rootProject.file("version.properties")
val versionProps = Properties()
if (versionPropsFile.exists()) versionProps.load(versionPropsFile.inputStream())
val versionName = versionProps.getProperty("VERSION_NAME", "1.0.0")

// ✅ Auto increment patch version
fun incrementVersion(version: String): String {
    val parts = version.split(".").map { it.toInt() }.toMutableList()
    parts[2]++ // bump patch version (e.g. 1.0.0 → 1.0.1)
    return parts.joinToString(".")
}

publishing {
    publications {
        create<MavenPublication>("release") {
            groupId = "com.dg.fendai"  // it must same with the name space of it !
            artifactId = "fendai"
            version = versionName
            afterEvaluate { from(components["release"]) }
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

// ✅ Update version.properties after successful publish
tasks.named("publish") {
    doLast {
        val newVersion = incrementVersion(versionName)
        versionProps["VERSION_NAME"] = newVersion
        versionProps.store(versionPropsFile.writer(), null)
        println("📦 Published version $versionName → Next version: $newVersion")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}


export USERNAME_GITHUB=your-github-username
export TOKEN_GITHUB=your-personal-access-token


gpr.user=your-github-username
gpr.key=your-personal-access-token

before you need to push tag first
./gradlew :fendai:publish

//git tag -a git tag -a 1.0.0-beta07.1.3 -m "Release 1.0.0-beta07.1.3" (new)


repositories {
    maven {
        url = uri("https://maven.pkg.github.com/SirJackSparow/fendai")
        credentials {
            username = "your-github-username"
            password = "your-personal-access-token"
        }
    }
}

dependencies {
    implementation("com.fendai:fendai:1.0.0") // or latest version
}


