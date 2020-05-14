package Storage

interface Peer {
    fun addPeers(infohash:String,peerData:Map<String, Any>)
    fun getPeers(infohash:String):Map<String, Any>?
    fun invalidatePeer(infohash:String,peerId:String)
    abstract fun getPeersList(infohash: String): List<Any>?
}