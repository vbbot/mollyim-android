// Build script for the vendored Light Phone SDK UI sources.
// Everything under src/ is MIT licensed, (c) 2026 The Light Phone -- see LICENSE and README.md.

plugins {
  id("signal-library")
  alias(libs.plugins.compose.compiler)
}

android {
  namespace = "com.thelightphone.sdk.ui"

  defaultConfig {
    consumerProguardFiles("consumer-rules.pro")
  }

  buildFeatures {
    compose = true
  }
}

dependencies {
  api(platform(libs.androidx.compose.bom))

  api(libs.androidx.compose.material3)
  api(libs.androidx.compose.ui.tooling.preview)
  debugApi(libs.androidx.compose.ui.tooling.core)

  implementation(libs.kotlinx.coroutines.core)

  // LightThemeController exposes a StateFlow; collectAsStateWithLifecycle is how callers consume it.
  // Declaring it here also drags the AndroidX lifecycle graph up to the version the rest of Molly
  // resolves (2.10.0, via :core:util) rather than the older one Material3 would otherwise pull in.
  implementation(libs.androidx.lifecycle.runtime.compose)

  constraints {
    // Resolved in isolation, this module selects androidx.collection 1.4.5 (requested by
    // material3-android) before collection-ktx later upgrades the group to 1.5.0. That transient
    // selection makes Gradle fetch collection-jvm-1.4.5's metadata, which is not covered by
    // gradle/verification-metadata.xml, so `./gradlew :core:lightui:<anything>` fails dependency
    // verification even though `:app` -- where the wider graph reaches 1.5.0 first -- does not.
    // Pinning to 1.5.0 keeps the module independently buildable and matches what :app ships.
    implementation("androidx.collection:collection") {
      version { strictly("1.5.0") }
      because("Match the androidx.collection version the rest of Molly resolves to; see above.")
    }
  }
}
