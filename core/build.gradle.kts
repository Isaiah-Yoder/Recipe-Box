import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The app's logic that doesn't need Android: reading recipe pages and cards,
// parsing and scaling ingredients, tagging, and building grocery lists.
// Keeping it free of Android lets its tests run fast on the computer, and lets
// another build, such as the Play app or a future server, reuse it unchanged.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(libs.jsoup)
    // jsoup's nullness annotations, which the compiler reads from its method types.
    compileOnly(libs.jspecify)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
