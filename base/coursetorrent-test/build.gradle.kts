import java.time.Duration
plugins {
}

val junitVersion: String? by extra
val hamkrestVersion: String? by extra
val guiceVersion: String? by extra
val kotlinGuiceVersion: String? by extra

dependencies {
    implementation(project(":library"))
    implementation(project(":coursetorrent-app"))

    testImplementation("org.junit.jupiter", "junit-jupiter-api", junitVersion)
    testImplementation("org.junit.jupiter", "junit-jupiter-params", junitVersion)
    testImplementation("com.natpryce", "hamkrest", hamkrestVersion)
    testImplementation("com.google.inject", "guice", guiceVersion)
    testImplementation("dev.misfitlabs.kotlinguice4", "kotlin-guice", kotlinGuiceVersion)

    testImplementation("io.mockk", "mockk", "1.9.3")
    runtimeOnly("org.junit.jupiter", "junit-jupiter-engine", junitVersion)
}

tasks.test{
    useJUnitPlatform()

	 minHeapSize = "256m"
    maxHeapSize = "4g"
    
    // Make sure tests don't take over 20 minutes
    timeout.set(Duration.ofMinutes(20))
	
    /**reports{
        junitXml.isEnabled = false
        html.isEnabled = true

        html.destination = File("PATH\\TO\\HTML")
    } 

    testLogging{
        val csvFile=File("PATH\\TO\\CSV")
        var toprow ="ClassName,TestName,Result,Duration(ms)\n"


        var content = ""
        addTestListener(object : TestListener {
            override fun beforeSuite(suite: TestDescriptor) {
                if(suite.parent == null){
                    csvFile.appendText(toprow)
                }
            }
            override fun beforeTest(testDescriptor: TestDescriptor) {}
            override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {
                content += testDescriptor.getClassName()+","+
                        testDescriptor.getName()+","+
                        result.resultType.toString()+","+
                        (result.endTime-result.startTime).toString()+"\n"

            }
            override fun afterSuite(suite: TestDescriptor, result: TestResult) {
                if(suite.parent == null){
                    println("Logging to csv at "+csvFile.absolutePath)
                    csvFile.appendText(content)
                    content=""
                }

            }
        })
    }*/

}



