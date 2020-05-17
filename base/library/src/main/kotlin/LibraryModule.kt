import Utils.statsStorage
import Utils.torrentStorage
import Utils.peerStorage
import Storage.*
import Storage.PeerStorage
import Storage.TorrentStorage
import com.google.inject.Inject
import com.google.inject.Provides
import com.google.inject.Singleton
import dev.misfitlabs.kotlinguice4.KotlinModule
import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import il.ac.technion.cs.softwaredesign.storage.SecureStorageFactory
//import il.ac.technion.cs.softwaredesign.storage.impl.SecureStorageFactoryImpl
//import il.ac.technion.cs.softwaredesign.storage.impl.SecureStorageImpl

class LibraryModule : KotlinModule() {
    override fun configure() {
        bind<Peer>().to<PeerStorage>()
        bind<Statistics>().to<StatisticsStorage>()
        bind<Torrent>().to<TorrentStorage>()
        //TODO: super mega important: add import for softwaredesign.storage.impl for staff implementation
        //TODO: remove??? TestStorage/TestStorageFactory ??
        bind<SecureStorageFactory>().toInstance(SecureStorageFactoryImpl())
        bind<SecureStorage>().toInstance(SecureStorageImpl())

    }

    //Binding with annotations(tutorial on Guice )
    @Provides
    @Singleton
    @Inject
    @torrentStorage
    fun provideTorrentStorage(factory: SecureStorageFactory):  SecureStorage {
        return factory.open("torrent".toByteArray())

    }

    @Provides
    @Singleton
    @Inject
    @statsStorage
    fun provideStatsStorage(factory: SecureStorageFactory):  SecureStorage {
        return factory.open("statistics".toByteArray())
    }

    @Provides
    @Singleton
    @Inject
    @peerStorage
    fun providePeerStorage(factory: SecureStorageFactory):  SecureStorage {
        return factory.open("peers".toByteArray())
    }
}