package Storage


interface Statistics {

    fun addTrackerStats(infohash:String,statsMap:Map<String,Any>)
    fun getStats(infohash:String):Map<String,Any>?
    fun updateStats(infohash:String,statsMap:Map<String,Any>)
}