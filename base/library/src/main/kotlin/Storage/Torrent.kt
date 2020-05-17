package Storage

interface Torrent {
    fun addTorrent(infohash:String,torrentData:ByteArray)
    fun removeTorrent(infohash:String,unloadValue:String)
    fun getTorrentData(infohash:String):ByteArray?
    fun updateAnnounceList(infohash:String,announceList:List<List<String>>)
}