package il.ac.technion.cs.softwaredesign

import LibraryModule
import dev.misfitlabs.kotlinguice4.KotlinModule

class CourseTorrentModule : KotlinModule() {
    override fun configure() {
        install(LibraryModule())
    }
}

