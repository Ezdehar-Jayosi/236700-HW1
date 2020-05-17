package il.ac.technion.cs.softwaredesign

import LibraryModule
//import il.ac.technion.cs.softwaredesign.storage.*
import dev.misfitlabs.kotlinguice4.KotlinModule

class CourseTorrentModule : KotlinModule() {
    override fun configure() {
        install(LibraryModule())
        bind<CourseTorrent>().to<CourseTorrentImpl>()
        //bind<SecureStorageFactory>().toInstance(SecureStorageFactoryImpl())
        //bind<SecureStorage>().toInstance(SecureStorageImpl())
    }
}

