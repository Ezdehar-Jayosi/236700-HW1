package il.ac.technion.cs.softwaredesign

import Storage.Peer
import Storage.Statistics
import Storage.Torrent
import com.google.inject.Inject
import il.ac.technion.cs.softwaredesign.exceptions.TrackerException
import Utils.*
import java.io.*
import java.net.URLEncoder
import java.security.MessageDigest
import java.net.InetAddress

/**
 * This is the class implementing CourseTorrent, a BitTorrent client.
 *
 * Currently specified:
 * + Parsing torrent metainfo files (".torrent" files)
 * + Communication with trackers (announce, scrape).
 */
class CourseTorrentImpl @Inject constructor(
    private val statStorage: Statistics,
    private val peerStorage: Peer,
    private val torrentStorage: Torrent,
    private val httpRequest: HTTPGet = HTTPGet()

) : CourseTorrent {
    private val encoding = Charsets.UTF_8
    private val unloadedVal = "unloaded"
    private val charList: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
    private val randomString = (1..6)
        .map { _ -> kotlin.random.Random.nextInt(0, charList.size) }
        .map(charList::get)
        .joinToString("")

    /**
     * Load in the torrent metainfo file from [torrent]. The specification for these files can be found here:
     * [Metainfo File Structure](https://wiki.theory.org/index.php/BitTorrentSpecification#Metainfo_File_Structure).
     *
     * After loading a torrent, it will be available in the system, and queries on it will succeed.
     *
     * This is a *create* command.
     *
     * @throws IllegalArgumentException If [torrent] is not a valid metainfo file.
     * @throws IllegalStateException If the infohash of [torrent] is already loaded.
     * @return The infohash of the torrent, i.e., the SHA-1 of the `info` key of [torrent].
     */
    override fun load(torrent: ByteArray): String {
        val value = Bencoding.DecodeObjectM(torrent) ?: throw IllegalArgumentException()
        val info_hash = Bencoding.infohash(torrent)
        val existing_entry = torrentStorage.getTorrentData(info_hash)
        if (existing_entry != null)
            if (existing_entry.toString(encoding) != unloadedVal)
                throw IllegalStateException()
        val bos = ByteArrayOutputStream()
        val oos = ObjectOutputStream(bos)
        oos.writeObject(Bencoding.Announce(value))
        oos.flush()
        val data = bos.toByteArray()
        torrentStorage.addTorrent(info_hash, data)
        return info_hash
    }

    /**
     * Remove the torrent identified by [infohash] from the system.
     *
     * This is a *delete* command.
     *
     * @throws IllegalArgumentException If [infohash] is not loaded.
     */
    override fun unload(infohash: String): Unit {
        val previousValue = torrentStorage.getTorrentData(infohash) ?: throw IllegalArgumentException()
        if (previousValue.toString(encoding) == unloadedVal) throw IllegalArgumentException()
        torrentStorage.removeTorrent(infohash, unloadedVal)
        statStorage.updateStats(infohash, mapOf(unloadedVal to Scrape(0,0,0,null)))
        peerStorage.addPeers(infohash, listOf(KnownPeer("",0,unloadedVal)))
    }

    /**
     * Return the announce URLs for the loaded torrent identified by [infohash].
     *
     * See [BEP 12](http://bittorrent.org/beps/bep_0012.html) for more information. This method behaves as follows:
     * * If the "announce-list" key exists, it will be used as the source for announce URLs.
     * * If "announce-list" does not exist, "announce" will be used, and the URL it contains will be in tier 1.
     * * The announce URLs should *not* be shuffled.
     *
     * This is a *read* command.
     *
     * @throws IllegalArgumentException If [infohash] is not loaded.
     * @return Tier lists of announce URLs.
     */
    override fun announces(infohash: String): List<List<String>> {
        val previousValue = torrentStorage.getTorrentData(infohash) ?: throw IllegalArgumentException()
        if (previousValue.toString(encoding) == unloadedVal) throw IllegalArgumentException()
        val bis = ByteArrayInputStream(previousValue)
        val inl: ObjectInput = ObjectInputStream(bis)
        val obj = inl.readObject()
        return obj as List<List<String>>
        //return previousValue as List<List<String>>
    }

    /**
     * Send an "announce" HTTP request to a single tracker of the torrent identified by [infohash], and update the
     * internal state according to the response. The specification for these requests can be found here:
     * [Tracker Protocol](https://wiki.theory.org/index.php/BitTorrentSpecification#Tracker_HTTP.2FHTTPS_Protocol).
     *
     * If [event] is [TorrentEvent.STARTED], shuffle the announce-list before selecting a tracker (future calls to
     * [announces] should return the shuffled list). See [BEP 12](http://bittorrent.org/beps/bep_0012.html) for more
     * information on shuffling and selecting a tracker.
     *
     * [event], [uploaded], [downloaded], and [left] should be included in the tracker request.
     *
     * The "compact" parameter in the request should be set to "1", and the implementation should support both compact
     * and non-compact peer lists.
     *
     * Peer ID should be set to "-CS1000-{Student ID}{Random numbers}", where {Student ID} is the first 6 characters
     * from the hex-encoded SHA-1 hash of the student's ID numbers (i.e., `hex(sha1(student1id + student2id))`), and
     * {Random numbers} are 6 random characters in the range [0-9a-zA-Z] generated at instance creation.
     *
     * If the connection to the tracker failed or the tracker returned a failure reason, the next tracker in the list
     * will be contacted and the announce-list will be updated as per
     * [BEP 12](http://bittorrent.org/beps/bep_0012.html).
     * If the final tracker in the announce-list has failed, then a [TrackerException] will be thrown.
     *
     * This is an *update* command.
     *
     * @throws TrackerException If the tracker returned a "failure reason". The failure reason will be the exception
     * message.
     * @throws IllegalArgumentException If [infohash] is not loaded.
     * @return The interval in seconds that the client should wait before announcing again.
     */
    override fun announce(infohash: String, event: TorrentEvent, uploaded: Long, downloaded: Long, left: Long): Int {
        val previousValue = torrentStorage.getTorrentData(infohash) ?: throw IllegalArgumentException()
        if (previousValue.toString() == unloadedVal) throw IllegalArgumentException()
        var peerList : List<KnownPeer> = (peerStorage.getPeers(infohash) ?: emptyList<KnownPeer>()) as List<KnownPeer>
        if(peerList.isNotEmpty())
            if (peerList[0] == KnownPeer("",0,unloadedVal)) peerList = emptyList<KnownPeer>()
        var statsMap : Map<String, ScrapeData> = (statStorage.getStats(infohash) ?: emptyMap<String, ScrapeData>()) as Map<String, ScrapeData>
        if (statsMap.containsKey(unloadedVal)) statsMap = emptyMap<String, ScrapeData>()
        val encoding = "UTF-8"
        val IDsumHash = MessageDigest.getInstance("SHA-1").digest((315737809 + 313380164).toString().toByteArray())
        val IDsumHashPart = IDsumHash
            .map { i -> "%x".format(i) }
            .joinToString("")
            .take(6)
        val peer_id = "-CS1000-$IDsumHashPart$randomString"
        val port = "6885"
        val request_params = createAnnounceRequestParams(infohash, event, uploaded, downloaded, left, peer_id, port)
        var announce_list = announces(infohash) as MutableList<MutableList<String>>
        if (event == TorrentEvent.STARTED)
            announce_list = announce_list.map { list -> list.shuffled() } as MutableList<MutableList<String>>
        var latest_failure : String = ""
        var good_announce : String = ""
        var interval : Int = 0
        var success = false
        for (announce_tier in announce_list) {
            for (announce_url in announce_tier){
                if (success) break
                val data = httpRequest.httpGET(announce_url, request_params)
                if(httpRequest.connectionSuccess == false) {
                    latest_failure = data as String
                }
                else {
                    val announceResponse = Bencoding.DecodeObjectM(data as ByteArray)
                    if(announceResponse?.containsKey("failure reason")!!)
                        latest_failure = announceResponse["failure reason"] as String
                    else {
                        latest_failure = ""
                        good_announce= announce_url
                        val knownPeersList = peerList.toMutableList()
                        if( announceResponse["peers"] is List<*>){//Bencoding.DecodeObject(peers) is Map<*,*>){
                            (announceResponse["peers"] as List<*>).forEach{ peers ->
                                val peerDict = (peers as Map<String,Any>)
                                val peerIP = decodeIP(peerDict["ip"] as String?)
                                val newPeer = KnownPeer(peerIP, peerDict["port"] as Int, peerDict["peer id"] as String?)
                                knownPeersList.filter { !(it.ip == newPeer.ip && it.port == newPeer.port)}
                                knownPeersList.add(KnownPeer(newPeer.ip, newPeer.port, newPeer.peerId))
                            }
                        } else {
                            val peers :ByteArray = announceResponse["peers"] as ByteArray
                            val segmentedPeerList = peers.asList().chunked(6)
                            for (portIP in segmentedPeerList) {
                                val peerIP = InetAddress.getByAddress(portIP.take(4).toByteArray()).hostAddress
                                val peerPort = ((portIP[4].toUByte().toInt() shl 8) + (portIP[5].toUByte().toInt()))
                                knownPeersList.filter { !(it.ip == peerIP.toString() && it.port == peerPort)}
                                knownPeersList.add(KnownPeer(peerIP.toString(), peerPort, null))
                            }

                        }
                        peerStorage.addPeers(infohash, knownPeersList)
                        interval = announceResponse["interval"] as Int
                        success = true
                        val scrapeData : ScrapeData = Scrape((announceResponse["complete"]?:0 )as Int,
                            (announceResponse["downloaded"]?:0) as Int,
                            (announceResponse["incomplete"]?:0) as Int,
                            announceResponse["name"] as String?) as ScrapeData
                        val newMap = statsMap.plus(Pair(announce_url.split("/").dropLast(1).joinToString("/"), scrapeData))
                        statStorage.updateStats(infohash, newMap)
                        }
                    }
                if(success == true) continue
            }
            if(success) {
                announce_tier.remove(good_announce)
                announce_tier.add(0, good_announce)
                break
            }
        }
        if(latest_failure != "")
            throw TrackerException(latest_failure)
        torrentStorage.updateAnnounceList(infohash, announce_list as List<List<String>>)
        return interval
    }
    /**
     * Scrape all trackers identified by a torrent, and store the statistics provided. The specification for the scrape
     * request can be found here:
     * [Scrape Protocol](https://wiki.theory.org/index.php/BitTorrentSpecification#Tracker_.27scrape.27_Convention).
     *
     * All known trackers for the torrent will be scraped.
     *
     * This is an *update* command.
     *
     * @throws IllegalArgumentException If [infohash] is not loaded.
     */
    override fun scrape(infohash: String): Unit {
        val previousValue = torrentStorage.getTorrentData(infohash) ?: throw IllegalArgumentException()
        if (previousValue.toString(encoding) == unloadedVal) throw IllegalArgumentException()
        var statsMap : Map<String, ScrapeData> = (statStorage.getStats(infohash) ?: emptyMap<String, ScrapeData>()) as Map<String, ScrapeData>
        if (statsMap.containsKey(unloadedVal)) statsMap = emptyMap<String, ScrapeData>()
        val requestParams = URLEncoder.encode("info_hash", "UTF-8") + "=" + Bencoding.urlInfohash(infohash)
        val announceList = announces(infohash)
        for (announce_tier in announceList) {
            for (announce_url in announce_tier) {
                val splitAnnounce = announce_url.split("/")
                val splitScrape = splitAnnounce.dropLast(1) + Regex("^announce").replace(splitAnnounce.last(), "scrape")
                val scrapeUrl = splitScrape.joinToString("/")
                val urlName = announce_url//splitAnnounce.dropLast(1).joinToString("/")
                val data = httpRequest.httpGET(scrapeUrl, requestParams)
                if(!httpRequest.connectionSuccess) {
                    statsMap = statsMap.filter { (k, _) -> k != urlName}
                    statsMap = statsMap.plus(Pair(urlName, Failure(reason = "Connection Failure")))                }
                else {
                    val scrapeResponse = Bencoding.DecodeObjectM(data as ByteArray) ?: throw IllegalArgumentException()
                    if((scrapeResponse as Map<*,*>).containsKey("failure reason")){
                        statsMap = statsMap.filter { (k, _) -> k != urlName}
                        statsMap = statsMap.plus(Pair(urlName, Failure(reason = (scrapeResponse["failure reason"] as String))))
                    } else {
                        val statsDict = scrapeResponse["files"] as Map<*,*>
                        if(statsDict.isEmpty()) {
                            statsMap = statsMap.filter { (k, _) -> k != urlName}
                            statsMap = statsMap.plus(Pair(urlName, Failure(reason = "not specified")))
                        } else {
                            print(statsDict[infohash])
                            val statsValues = statsDict.values.toList()[0] as Map<*, *>
                            print(statsValues)
                            val scrapeData: ScrapeData = Scrape(
                                    statsValues["complete"] as Int,
                                    statsValues["downloaded"] as Int,
                                    statsValues["incomplete"] as Int,
                                    statsValues["name"] as String?
                            ) as ScrapeData
                            statsMap = statsMap.filter { (k, _) -> k != urlName}
                            statsMap = statsMap.plus(Pair(urlName, scrapeData))
                        }
                    }
                }
            }
        }
        statStorage.updateStats(infohash, statsMap)

    }



    /**
     * Invalidate a previously known peer for this torrent.
     *
     * If [peer] is not a known peer for this torrent, do nothing.
     *
     * This is an *update* command.
     *
     * @throws IllegalArgumentException If [infohash] is not loaded.
     */
    override fun invalidatePeer(infohash: String, peer: KnownPeer): Unit {
        val torrent = torrentStorage.getTorrentData(infohash) ?: throw IllegalArgumentException()
        if (torrent.toString(encoding) == unloadedVal) throw IllegalArgumentException()
        var peerList : List<KnownPeer> = (peerStorage.getPeers(infohash) ?: emptyList<KnownPeer>()) as List<KnownPeer>
        if(peerList.isNotEmpty())
            if (peerList[0] == KnownPeer("",0,unloadedVal))
                peerList = emptyList<KnownPeer>()
        val newList = peerList.filter{ !(it.ip==peer.ip && it.port == peer.port) }
        peerStorage.addPeers(infohash, newList)

    }


    /**
     * Return all known peers for the torrent identified by [infohash], in sorted order. This list should contain all
     * the peers that the client can attempt to connect to, in ascending numerical order. Note that this is not the
     * lexicographical ordering of the string representation of the IP addresses: i.e., "127.0.0.2" should come before
     * "127.0.0.100".
     *
     * The list contains unique peers, and does not include peers that have been invalidated.
     *
     * This is a *read* command.
     *
     * @throws IllegalArgumentException If [infohash] is not loaded.
     * @return Sorted list of known peers.
     */
    override fun knownPeers(infohash: String): List<KnownPeer> {
        val torrent = torrentStorage.getTorrentData(infohash) ?: throw IllegalArgumentException()
        if (torrent.toString(encoding) == unloadedVal) throw IllegalArgumentException()
        var list : List<KnownPeer> = (peerStorage.getPeers(infohash) ?: emptyList<KnownPeer>()) as List<KnownPeer>
        if(list.isNotEmpty())
            if (list[0] == KnownPeer("",0,unloadedVal))
                list = emptyList()

        return list.sortedBy {
            it.ip.split(".").asSequence().map { "%02x".format(it.toInt().toByte()) }.toList()
                .joinToString(separator = "") { it }
        }
    }

    /**
     * Return all known statistics from trackers of the torrent identified by [infohash]. The statistics displayed
     * represent the latest information seen from a tracker.
     *
     * The statistics are updated by [announce] and [scrape] calls. If a response from a tracker was never seen, it
     * will not be included in the result. If one of the values of [ScrapeData] was not included in any tracker response
     * (e.g., "downloaded"), it would be set to 0 (but if there was a previous result that did include that value, the
     * previous result would be shown).
     *
     * If the last response from the tracker was a failure, the failure reason would be returned ([ScrapeData] is
     * defined to allow for this). If the failure was a failed connection to the tracker, the reason should be set to
     * "Connection failed".
     *
     * This is a *read* command.
     *
     * @throws IllegalArgumentException If [infohash] is not loaded.
     * @return A mapping from tracker announce URL to statistics.
     */
    override fun trackerStats(infohash: String): Map<String, ScrapeData> {

        val torrent = torrentStorage.getTorrentData(infohash) ?: throw IllegalArgumentException()
        if (torrent.toString(encoding) == unloadedVal) throw IllegalArgumentException()
        val statmap = statStorage.getStats(infohash) as Map<String, ScrapeData>
        return statmap
    }


    /*******************************Private Functions*****************************/

    private fun fromStringToInteger(Ip: String): Int {
        var num: Int = 0
        var itr = 0
        while (itr != Ip.length) {
            val char: Char = Ip[itr].toChar()
            num = (num * 10) + (char - '0')
            itr++

        }
        return num
    }
    private fun createAnnounceRequestParams(infohash: String, event: TorrentEvent, uploaded: Long, downloaded: Long, left: Long, peerID: String, port: String ): String {
        var request_params = URLEncoder.encode("info_hash", encoding) + "=" + Bencoding.urlInfohash(infohash)
        request_params += "&" + URLEncoder.encode("peer_id", encoding) + "=" + URLEncoder.encode(peerID, encoding)
        request_params += "&" + URLEncoder.encode("port", encoding) + "=" + URLEncoder.encode(port, encoding)
        request_params += "&" + URLEncoder.encode("uploaded", encoding) + "=" + URLEncoder.encode(uploaded.toString(), encoding)
        request_params += "&" + URLEncoder.encode("downloaded", encoding) + "=" + URLEncoder.encode(downloaded.toString(), encoding)
        request_params += "&" + URLEncoder.encode("left", encoding) + "=" + URLEncoder.encode(left.toString(), encoding)
        request_params += "&" + URLEncoder.encode("compact", encoding) + "=" + URLEncoder.encode("1", encoding)
        request_params += "&" + URLEncoder.encode("event", encoding) + "=" + URLEncoder.encode(event.toString(), encoding)
        return request_params
    }
    private fun decodeIP(ip: String?) : String {
        if(ip == null) return "0.0.0.0"
        if(ip.toByteArray().size == 4) {
            return InetAddress.getByAddress(ip.toByteArray()).hostAddress
        }
        else {
            return ip
        }
    }
}