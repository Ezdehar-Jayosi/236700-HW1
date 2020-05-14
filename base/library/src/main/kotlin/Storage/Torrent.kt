package Storage

interface Torrent {
    fun addTorrent(infohash:String,torrentData:Any)
    fun removeTorrent(infohash:String,unloadValue:String)
    fun getTorrentData(infohash:String):ByteArray?
    fun updateAnnounceList(infohash:String,announceList:List<List<Any>>)
}