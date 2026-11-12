plugins {
    id("com.android.application")
}

android {
    // Mesmo namespace/applicationId do app Flutter: a Data Layer exige os dois iguais e com a mesma assinatura.
    namespace = "com.zdearo.vibealarm"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.zdearo.vibealarm"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("com.google.android.gms:play-services-wearable:19.0.0")
    implementation("androidx.health:health-services-client:1.0.0") // EXPERIMENTAL: estado "dormindo" do Health Services
    implementation("com.google.guava:listenablefuture:1.0") // tipo ListenableFuture, usado pela API acima
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303") // o org.json do Android é só um stub nos testes de unidade
}
