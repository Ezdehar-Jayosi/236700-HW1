package il.ac.technion.cs.softwaredesign

import Storage.Peer
import Storage.Statistics
import Storage.Torrent
import com.google.inject.Inject
import il.ac.technion.cs.softwaredesign.exceptions.TrackerException
import Utils.*
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/**
 * This is the class implementing CourseTorrent, a BitTorrent client.
 *
 * Currently specified:
 * + Parsing torrent metainfo files (".torrent" files)
 * + Communication with trackers (announce, scrape).
 */
class CourseTorrent @Inject constructor(
    private val statStorage: Statistics,
    private val peerStorage: Peer,
    private val torrentStorage: Torrent
) {
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
    fun load(torrent: ByteArray): String {
        val value = Bencoding.DecodeObjectM(torrent) ?: throw IllegalArgumentException()
        if (value == null) throw IllegalArgumentException()
        val info_hash = Bencoding.infohash(torrent)
        val existing_entry = torrentStorage.getTorrentData(info_hash)
        if (existing_entry != null)
            if (existing_entry.toString(Charsets.UTF_8) != unloadedVal)
                throw IllegalStateException()
//        val bos = ByteArrayOutputStream()
//        val oos = ObjectOutputStream(bos)
//        oos.writeObject(Bencoding.Announce(value))
//        oos.flush()
//        val data = bos.toByteArray()
        //TODO: initialize peer and stats storage for this torrent?
        torrentStorage.addTorrent(info_hash, Conversion.toByteArray(Bencoding.Announce(value)) as ByteArray)
        return info_hash
    }

    /**
     * Remove the torrent identified by [infohash] from the system.
     *
     * This is a *delete* command.
     *
     * @throws IllegalArgumentException If [infohash] is not loaded.
     */
    fun unload(infohash: String): Unit {
        val previous_value = torrentStorage.getTorrentData(infohash) ?: throw IllegalArgumentException()
        if (previous_value.toString() == unloadedVal) throw IllegalArgumentException()
        torrentStorage.removeTorrent(infohash, unloadedVal)
        //TODO: delete torrent from peer and stats storage too?
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
    fun announces(infohash: String): List<List<String>> {
        val previous_value = torrentStorage.getTorrentData(infohash) ?: throw IllegalArgumentException()
        if (previous_value.toString() == unloadedVal) throw IllegalArgumentException()
//        val bis = ByteArrayInputStream(previous_value)
//        val inl: ObjectInput = ObjectInputStream(bis)
//        val obj = inl.readObject() as List<List<String>> //TODO: use a method to extract list?
        return return Conversion.fromByteArray(previous_value) as List<List<String>>
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
    fun announce(infohash: String, event: TorrentEvent, uploaded: Long, downloaded: Long, left: Long): Int {
        val encoding = "UTF-8"
        var request_params = URLEncoder.encode("info_hash", encoding) + "=" + URLEncoder.encode(infohash, encoding)
        val IDsumHash = MessageDigest.getInstance("SHA-1").digest((315737809 + 313380164).toString().toByteArray())
        val IDsumHashPart = IDsumHash
            .map { i -> "%x".format(i) }
            .joinToString("")
            .take(6)
        val peer_id = "-CS1000-$IDsumHashPart$randomString"
        val port = "6885"
        request_params += "&" + URLEncoder.encode("peer_id", encoding) + "=" + URLEncoder.encode(peer_id, encoding)
        request_params += "&" + URLEncoder.encode("port", encoding) + "=" + URLEncoder.encode(port, encoding)
        request_params += "&" + URLEncoder.encode("uploaded", encoding) + "=" + URLEncoder.encode(
            uploaded.toString(),
            encoding
        )
        request_params += "&" + URLEncoder.encode(
            "downloaded",
            encoding
        ) + "=" + URLEncoder.encode(downloaded.toString(), encoding)
        request_params += "&" + URLEncoder.encode("left", encoding) + "=" + URLEncoder.encode(left.toString(), encoding)
        request_params += "&" + URLEncoder.encode("compact", encoding) + "=" + URLEncoder.encode("1", encoding)
        request_params += "&" + URLEncoder.encode("event", encoding) + "=" + URLEncoder.encode(
            event.toString(),
            encoding
        )

        var announce_list = announces(infohash)
        if (event == TorrentEvent.STARTED)
            announce_list = announce_list.map { list -> list.shuffled(kotlin.random.Random(123)) }
        for (announce_tier in announce_list) {
            var good_announce: String? = null
            for (announce_url in announce_tier) {
                val request = URL("$announce_url?$request_params")
                with(request.openConnection() as HttpURLConnection) {
                    requestMethod = "GET"
                    println("URL : $url")
                    println("Response Code : $responseCode")

                    BufferedReader(InputStreamReader(inputStream)).use {
                        val response = StringBuffer()

                        var inputLine = it.readLine()
                        while (inputLine != null) {
                            response.append(inputLine)
                            inputLine = it.readLine()
                        }
                        it.close()
                        println("Response : $response")

                        //todo: if(positive response)
                        // good_announce= announce url
                    }
                }
            }
        }


        //TODO: at the end, write announce_list to torrentStorage
        return 0
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
    fun scrape(infohash: String): Unit {

        val encoding = "UTF-8"
        val requestParams = URLEncoder.encode("info_hash", encoding) + "=" + URLEncoder.encode(infohash, encoding)
        val announceList = announces(infohash)
        for (announce_tier in announceList) {
            for (announce_url in announce_tier) {
                val splitAnnounce = announce_url.split("/")
                //TODO: do we assume every announce has a valid scrape?
                val splitScrape = splitAnnounce.dropLast(1) + Regex("^announce").replace(splitAnnounce.last(), "scrape")
                val scrapeUrl = splitScrape.joinToString("")
                val request = URL("$scrapeUrl?$requestParams")
                with(request.openConnection() as HttpURLConnection) {
                    requestMethod = "GET"
                    println("URL : $url")
                    println("Response Code : $responseCode")

                    BufferedReader(InputStreamReader(inputStream)).use {
                        val response = StringBuffer()

                        var inputLine = it.readLine()
                        while (inputLine != null) {
                            response.append(inputLine)
                            inputLine = it.readLine()
                        }
                        it.close()
                        println("Response : $response")

                        //if positive todo: save scrape to system
                    }
                }
            }
        }

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
    fun invalidatePeer(infohash: String, peer: KnownPeer): Unit {
        val torrent = torrentStorage.getTorrentData(infohash) ?: throw IllegalArgumentException()
        if (torrent.toString() == unloadedVal) throw IllegalArgumentException()
        peer.peerId?.let { peerStorage.invalidatePeer(infohash, it) }

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
    fun knownPeers(infohash: String): List<KnownPeer> {
        val torrent = torrentStorage.getTorrentData(infohash) ?: throw IllegalArgumentException()
        if (torrent.toString() == unloadedVal) throw IllegalArgumentException()
        val list = peerStorage.getPeersList(infohash) as List<KnownPeer>
        val peerComparator =
            Comparator<KnownPeer> { a: KnownPeer, b: KnownPeer ->
                val x = fromStringToInteger(a.ip.replace(".", ""))
                val y = fromStringToInteger(b.ip.replace(".", ""))
                when {
                    (y >= x) -> 0
                    (x > y) -> -1
                    else -> -1
                }
            }
        return list.sortedWith(peerComparator)
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
    fun trackerStats(infohash: String): Map<String, ScrapeData> {

        val torrent = torrentStorage.getTorrentData(infohash) ?: throw IllegalArgumentException()
        if (torrent.toString() == unloadedVal) throw IllegalArgumentException()
        val statmap = statStorage.getStats(infohash) as Map<String, ScrapeData>
        return statmap
        //TODO(CHECK IF THE STATISTICS ARE UPDATED BY THE SCRAP FUNCTION)
    }

    /*******************************Private Functions*****************************/

    private fun fromStringToInteger(Ip: String): Int {
        var num: Int = 0
        var itr = 0
        while (itr != Ip.length) {
            var char: Char = Ip[itr].toChar()
            num = (num * 10) + (char - '0')
            itr++

        }
        return num
    }
}