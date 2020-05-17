package il.ac.technion.cs.softwaredesign

import LibraryModule
import Storage.SecureStorageFactoryImpl
import Storage.SecureStorageImpl
import dev.misfitlabs.kotlinguice4.KotlinModule
//import il.ac.technion.cs.softwaredesign.storage.SecureStorage
//import il.ac.technion.cs.softwaredesign.storage.SecureStorageFactory

class CourseTorrentTestModule: KotlinModule()  {
    override fun configure() {
        install(LibraryModule())
        bind<CourseTorrent>().to<CourseTorrentImpl>()
        //bind<SecureStorageFactory>().toInstance(SecureStorageFactoryImpl())
        //bind<SecureStorage>().toInstance(SecureStorageImpl(HashMap<ByteArray,ByteArray>()))

    }
}
