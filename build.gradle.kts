plugins {
    alias(libs.plugins.android.application) apply false
}

// Keep build outputs outside OneDrive so background sync never locks incremental build files.
allprojects {
    layout.buildDirectory.set(
        file("${System.getProperty("user.home")}/.android-build/AISummaryFlashcard/${project.name}")
    )
}
