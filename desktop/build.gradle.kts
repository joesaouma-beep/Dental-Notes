import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":desktop-core"))

    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}

compose.desktop {
    application {
        mainClass = "com.dentalstudio.notes.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb)
            packageName = "Dental Notes"
            packageVersion = "1.0.0"
            description = "Dictated dental treatment notes that learn from your edits"
            vendor = "Dental Studio"

            windows {
                menuGroup = "Dental Notes"
                perUserInstall = true
                dirChooser = true
                shortcut = true
                // Fixed so future versions upgrade in place rather than
                // installing alongside the old one.
                upgradeUuid = "8F3C1D42-6A9E-4B77-9C2E-5D4A1B0E7F63"
            }

            modules("java.sql", "java.naming", "jdk.unsupported")
        }
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("passed", "failed", "skipped") }
}
