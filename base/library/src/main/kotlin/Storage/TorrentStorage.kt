package Storage
import Utils.Conversion
import Utils.Conversion.fromByteArray
import Utils.Conversion.toByteArray
import Utils.torrentStorage
import com.google.inject.Singleton
import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import com.google.inject.Inject

@Singleton
class TorrentStorage @Inject constructor(
    @torrentStorage private val torrentStorage: SecureStorage
) : Torrent {
    override fun addTorrent(infohash: String, torrentData: ByteArray) {
        torrentStorage.write(infohash.toByteArray(), torrentData)

    }
    override fun removeTorrent(infohash:String,unloadValue:String) {
        torrentStorage.write(infohash.toByteArray(Charsets.UTF_8), unloadValue.toByteArray(Charsets.UTF_8))
    }

    override fun getTorrentData(infohash: String): ByteArray? {
        return torrentStorage.read(infohash.toByteArray(Charsets.UTF_8))

    }

    override fun updateAnnounceList(infohash: String, announceList: List<List<String>>) {
        val retVlaue= torrentStorage.read(infohash.toByteArray())
        if(retVlaue!=null && (fromByteArray(retVlaue).toString() !="unloaded" )) {
            torrentStorage.write(infohash.toByteArray(Charsets.UTF_8), toByteArray(announceList) as ByteArray)
        }
    }
}