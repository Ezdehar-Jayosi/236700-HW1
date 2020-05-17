plugins {
    application
}

application {
    mainClassName = "il.ac.technion.cs.softwaredesign.MainKt"
}
repositories {
    jcenter()
}
val junitVersion: String? by extra
val hamkrestVersion: String? by extra
val guiceVersion: String? by extra
val kotlinGuiceVersion: String? by extra
val mockkVersion: String? by extra
val externalLibraryVersion: String? by extra
val fuelVersion: String? by extra
val resultVersion: String? by extra

dependencies {
    implementation(project(":library"))
    implementation("il.ac.technion.cs.softwaredesign", "primitive-storage-layer", externalLibraryVersion)
    implementation ("com.github.kittinunf.fuel","fuel",fuelVersion)
    implementation("com.google.inject", "guice", guiceVersion)
    implementation("dev.misfitlabs.kotlinguice4", "kotlin-guice", kotlinGuiceVersion)
    implementation("com.github.kittinunf.result","result",resultVersion)

    testImplementation("org.junit.jupiter", "junit-jupiter-api", junitVersion)
    testImplementation("org.junit.jupiter", "junit-jupiter-params", junitVersion)
    testImplementation("com.natpryce", "hamkrest", hamkrestVersion)
	testImplementation("io.mockk", "mockk", mockkVersion)

}
