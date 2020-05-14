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

class LibraryModule : KotlinModule() {
    override fun configure() {
        bind<Peer>().to<PeerStorage>()
        bind<Statistics>().to<StatisticsStorage>()
        bind<Torrent>().to<TorrentStorage>()
    }

    //Binding with annotations(tutorial on Guice )
    @Provides
    @Singleton
    @Inject
    fun provideTorrentStorage(factory: SecureStorageFactory): @torrentStorage SecureStorage {
        return factory.open("torrent".toByteArray())

    }

    @Provides
    @Singleton
    @Inject
    fun provideStatsStorage(factory: SecureStorageFactory): @statsStorage SecureStorage {
        return factory.open("statistics".toByteArray())
    }

    @Provides
    @Singleton
    @Inject
    fun providePeerStorage(factory: SecureStorageFactory): @peerStorage SecureStorage {
        return factory.open("peers".toByteArray())
    }
}