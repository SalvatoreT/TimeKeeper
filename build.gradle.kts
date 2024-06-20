plugins {
    id("com.android.application")
}

android {
    defaultConfig {
        applicationId = "dev.sal.timekeeper"
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("VERSION_NAME")
    }
}
