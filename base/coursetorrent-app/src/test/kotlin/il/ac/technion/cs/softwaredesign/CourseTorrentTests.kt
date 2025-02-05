package il.ac.technion.cs.softwaredesign


import Utils.HTTPGet
import com.google.inject.Guice
import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import dev.misfitlabs.kotlinguice4.KotlinModule
import dev.misfitlabs.kotlinguice4.getInstance
import il.ac.technion.cs.softwaredesign.exceptions.TrackerException
import io.mockk.*
//import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import org.junit.jupiter.api.*

class CourseTorrentTests {
    private val httpMock = mockk<HTTPGet>(relaxed=true)
    inner class TestModule : KotlinModule(){
        override fun configure() {
            bind<HTTPGet>().toInstance(httpMock)
        }
    }
    private val injector = Guice.createInjector(CourseTorrentTestModule(), TestModule())
    private val torrent = injector.getInstance<CourseTorrent>()
    private val debian = this::class.java.getResource("/debian-10.3.0-amd64-netinst.iso.torrent").readBytes()
    private val lame = this::class.java.getResource("/lame.torrent").readBytes()
    private val announceListTorrent = this::class.java.getResource("/announceListTorrent.torrent").readBytes()
    private val shuffleableTorrent = this::class.java.getResource("/gt_chrY.xml(3).torrent").readBytes()
    private val charset = Charsets.UTF_8



    @Test
    fun `after load, infohash calculated correctly`() {
        val infohash = torrent.load(debian)
        assertThat(infohash, equalTo("5a8062c076fa85e8056451c0d9aa04349ae27909"))
    }
    @Test
    fun `unloading or announcing a torrent that hasn't been loaded fails`() {
        assertThrows<IllegalArgumentException> { torrent.unload("5a8062c076fa85e8056451c0d9aa04349ae27909") }
        assertThrows<IllegalArgumentException> { torrent.announces("5a8062c076fa85e8056451c0d9aa04349ae27909") }
    }
    @Test
    fun `announce on unloaded torrent`() {
        val infohash = torrent.load(debian)
        torrent.unload(infohash)
        assertThrows<IllegalArgumentException> { torrent.announces(infohash) }
    }
    @Test
    fun `loading, unloading and reloading torrent`() {
        val infohash = torrent.load(debian)
        torrent.unload(infohash)
        torrent.load(debian)
        assertThat(infohash, equalTo("5a8062c076fa85e8056451c0d9aa04349ae27909"))
    }
    @Test
    fun `failing on loading the same torrent twice`() {
        torrent.load(debian)
        assertThrows<IllegalStateException> { torrent.load(debian) }
    }
    @Test
    fun `test correctness of announces`() {
        val infohash = torrent.load(debian)
        val announces = torrent.announces(infohash)
        assertThat(announces, allElements(hasSize(equalTo(1))))
        assertThat(announces, hasSize(equalTo(1)))
        assertThat(announces, allElements(hasElement("http://bttracker.debian.org:6969/announce")))
    }
    @Test
    fun `test correctness of announce-list`() {
        val infohash = torrent.load(announceListTorrent)
        val announces = torrent.announces(infohash)
        assertThat(announces, allElements(hasSize(equalTo(1))))
        assertThat(announces, hasSize(equalTo(8)))
        assertThat(announces, anyElement(hasElement("udp://62.138.0.158:6969/announce")))
    }
    @Test
    fun `loading multiple torrent and reading their announces`() {
        val infohash1 = torrent.load(debian)
        val infohash2 = torrent.load(announceListTorrent)
        val infohash3 = torrent.load(lame)
        val announces1 = torrent.announces(infohash1)
        val announces2 = torrent.announces(infohash2)
        val announces3 = torrent.announces(infohash3)
        assertThat(announces1, allElements(hasSize(equalTo(1))))
        assertThat(announces1, hasSize(equalTo(1)))
        assertThat(announces1, allElements(hasElement("http://bttracker.debian.org:6969/announce")))
        assertThat(announces2, allElements(hasSize(equalTo(1))))
        assertThat(announces2, hasSize(equalTo(8)))
        assertThat(announces2, anyElement(hasElement("udp://62.138.0.158:6969/announce")))
        assertThat(announces3, allElements(hasSize(equalTo(1))))
        assertThat(announces3, hasSize(equalTo(1)))
        assertThat(announces3, allElements(hasElement("https://127.0.0.1:8082/announce")))
    }
    @Test
    fun `loading, unloading and announcing for multiple torrents`(){
        val infohash2 = torrent.load(announceListTorrent)
        val infohash3 = torrent.load(debian)
        torrent.unload(infohash2)
        assertThrows<IllegalArgumentException> {   torrent.announces(infohash2)}
        torrent.load(announceListTorrent)
        val announces2 = torrent.announces(infohash2)
        //Loading a different torrent to check that loading the new torrent doesn't affect previous torrents
        torrent.load(lame)
        //assert that the announce remains for the old torrent
        assertThat(announces2, allElements(hasSize(equalTo(1))))
        assertThat(announces2, hasSize(equalTo(8)))
        assertThat(announces2, anyElement(hasElement("udp://62.138.0.158:6969/announce")))


    }

    @Test
    fun `announce to tracker`(){
        every{
            httpMock.httpGET(any(),any())
        } answers{
            "d8:intervali360e5:peers0:e".toByteArray(charset)
        }
        every{
            httpMock.connectionSuccess
        } returns true


        /* interval is 360 */
        val infohash = torrent.load(lame)
        val interval = torrent.announce(infohash, TorrentEvent.STARTED, 0, 0, 0)
        assertThat(interval, equalTo(360))
        /* Assertion to verify that the tracker was actually called */    }
    @Test
    fun `scrape tracker`(){
        val infohash = torrent.load(lame)
        every{
            httpMock.httpGET("https://127.0.0.1:8082/scrape",any())
        } answers {
            "d5:flagsd20:min_request_intervali900ee5:filesd20:infohashinfohashinfod8:completei778e10:incompletei1e10:downloadedi18241eeee".toByteArray(charset)
        }
        every{
            httpMock.connectionSuccess
        } returns true

        /* Tracker has infohash, 0 complete, 0 downloaded, 0 incomplete, no name key */
        assertDoesNotThrow { torrent.scrape(infohash) }

        assertThat(
            torrent.trackerStats(infohash),
            equalTo(mapOf(Pair("https://127.0.0.1:8082", Scrape(778, 18241, 1, null) as ScrapeData)))
        )
        /* Assertion to verify that the tracker was actually called */
    }
    @Test
    fun `announce, scrape fail on unloaded torrent`(){
        val infohash = "5a8062c076fa85e8056451c0d9aa04349ae27909"
        assertThrows<IllegalArgumentException>{torrent.announce(infohash, TorrentEvent.COMPLETED, 0,0,0)}
        assertThrows<IllegalArgumentException>{torrent.scrape(infohash)}
    }
    @Test
    fun `invalidate, knownPeers, stats fail on unloaded torrent`(){
        val infohash = "5a8062c076fa85e8056451c0d9aa04349ae27909"
        assertThrows<IllegalArgumentException>{torrent.invalidatePeer(infohash, KnownPeer("abc",5,"id"))}
        assertThrows<IllegalArgumentException>{torrent.knownPeers(infohash)}
        assertThrows<IllegalArgumentException> {torrent.trackerStats(infohash)}
    }
    @Test
    fun `invalidate unit test`(){
        val infohash = torrent.load(lame)

        every{
            httpMock.httpGET("https://127.0.0.1:8082/announce",any())
        } answers {
            "d8:intervali360e5:peers6:".toByteArray(charset) + byteArrayOf(127,0,0,22,26, 231.toByte()) + "e".toByteArray(charset)
        }
        every{
            httpMock.connectionSuccess
        } returns true
        /* Returned peer list is: [("127.0.0.22", 6887)] */
        torrent.announce(infohash, TorrentEvent.STARTED, 0, 0, 2703360)

        every{
            httpMock.httpGET("https://127.0.0.1:8082/announce",any())
        } answers {
            "d8:intervali360e5:peers6:".toByteArray(charset) + byteArrayOf(127,0,0,21,26, 233.toByte()) + "e".toByteArray(charset)
        }
        /* Returned peer list is: [("127.0.0.22", 6887), ("127.0.0.21", 6889)] */
        torrent.announce(infohash, TorrentEvent.REGULAR, 0, 81920, 2621440)

        torrent.invalidatePeer(infohash, KnownPeer("127.0.0.22", 6887, null))

        assertThat(
            torrent.knownPeers(infohash),
            anyElement(has(KnownPeer::ip, equalTo("127.0.0.22")) and has(KnownPeer::port, equalTo(6887))).not()
        )
    }
    @Test
    fun `peer list after announce`(){
        val infohash = torrent.load(lame)

        every{
            httpMock.httpGET("https://127.0.0.1:8082/announce",any())
        } answers {
            "d8:intervali360e5:peers6:".toByteArray(charset) + byteArrayOf(127,0,0,22,26, 231.toByte()) + "e".toByteArray(charset)
        }
        every{
            httpMock.connectionSuccess
        } returns true
        /* Returned peer list is: [("127.0.0.22", 6887)] */
        torrent.announce(infohash, TorrentEvent.STARTED, 0, 0, 2703360)

        every{
            httpMock.httpGET("https://127.0.0.1:8082/announce",any())
        } answers {
            "d8:intervali360e5:peers6:".toByteArray(charset) + byteArrayOf(127,0,0,21,26, 233.toByte()) + "e".toByteArray(charset)
        }
        every{
            httpMock.connectionSuccess
        } returns true
        /* Returned peer list is: [("127.0.0.22", 6887), ("127.0.0.21", 6889)] */
        torrent.announce(infohash, TorrentEvent.REGULAR, 0, 81920, 2621440)

        assertThat(
            torrent.knownPeers(infohash),
            anyElement(has(KnownPeer::ip, equalTo("127.0.0.22")) and has(KnownPeer::port, equalTo(6887)))
        )
        assertThat(
            torrent.knownPeers(infohash),
            anyElement(has(KnownPeer::ip, equalTo("127.0.0.21")) and has(KnownPeer::port, equalTo(6889)))
        )
        assertThat(
            torrent.knownPeers(infohash), equalTo(torrent.knownPeers(infohash).distinct())
        )
    }
    @Test
    fun `peer list after announce with peer dictionary format`(){
        val infohash = torrent.load(lame)

        every{
            httpMock.httpGET("https://127.0.0.1:8082/announce",any())
        } answers {
            "d8:intervali360e5:peersld7:peer id6:peerid2:ip10:127.0.0.224:porti6887eeee".toByteArray(charset)
        }
        every{
            httpMock.connectionSuccess
        } returns true
        /* Returned peer list is: [("127.0.0.22", 6887)] */
        torrent.announce(infohash, TorrentEvent.STARTED, 0, 0, 2703360)

        every{
            httpMock.httpGET("https://127.0.0.1:8082/announce",any())
        } answers {
            "d8:intervali360e5:peersld7:peer id7:peerid22:ip10:127.0.0.214:porti6889eeee".toByteArray(charset)
        }
        every{
            httpMock.connectionSuccess
        } returns true
        /* Returned peer list is: [("127.0.0.22", 6887), ("127.0.0.21", 6889)] */
        torrent.announce(infohash, TorrentEvent.REGULAR, 0, 81920, 2621440)

        assertThat(
            torrent.knownPeers(infohash),
            anyElement(has(KnownPeer::ip, equalTo("127.0.0.22")) and has(KnownPeer::port, equalTo(6887)))
        )
        assertThat(
            torrent.knownPeers(infohash),
            anyElement(has(KnownPeer::ip, equalTo("127.0.0.21")) and has(KnownPeer::port, equalTo(6889)))
        )
        assertThat(
            torrent.knownPeers(infohash), equalTo(torrent.knownPeers(infohash).distinct())
        )
    }
    @Test
    fun `announce urls properly shuffled`(){
        val infohash = torrent.load(shuffleableTorrent)
        every{
            httpMock.httpGET("udp://tracker.opentrackr.org:1337/announce",any())
        } answers {
            "d14:failure reason6:stupide".toByteArray(charset)
        }
        every{
            httpMock.httpGET("udp://open.stealth.si:80/announce",any())
        } answers {
            "d14:failure reason6:stupide".toByteArray(charset)
        }
        every{
            httpMock.httpGET("udp://9.rarbg.me:2710/announce",any())
        } answers {
            "d8:intervali360e5:peers6:".toByteArray(charset) + byteArrayOf(127,0,0,22,26, 231.toByte()) + "e".toByteArray(charset)
        }
        every{
            httpMock.connectionSuccess
        } returns true
        torrent.announce(infohash, TorrentEvent.REGULAR, 0, 45, 20045)
        val announceList = torrent.announces(infohash)
        assertThat(
            announceList[1][0],
            equalTo("udp://9.rarbg.me:2710/announce")
        )
    }
    @Test
    fun `scrape updates on connection failure`(){
        val infohash = torrent.load(announceListTorrent)
        every{
            httpMock.connectionSuccess
        } returns true
        every{
            httpMock.httpGET(any(),any())
        } answers {
            "d5:filesdee".toByteArray(charset)
        }
        every{
            httpMock.httpGET("http://legittorrents.info:2710/scrape",any())
        } answers {
            "d5:flagsd20:min_request_intervali900ee5:filesd20:infohashinfohashinfod8:completei778e10:incompletei1e10:downloadedi18241eeee".toByteArray(charset)
        }

        torrent.scrape(infohash)
        val oldScrape =torrent.trackerStats(infohash)["http://legittorrents.info:2710"]

        every{
            httpMock.connectionSuccess
        } returns false

        torrent.scrape(infohash)

        assertThat(
            torrent.trackerStats(infohash)["http://legittorrents.info:2710"],
            equalTo(oldScrape).not()
        )

    }
    @Test
    fun `multiple scrapes from one torrent, then updates on failure`(){
        val infohash = torrent.load(announceListTorrent)
        every{
            httpMock.connectionSuccess
        } returns true
        every{
            httpMock.httpGET(any(),any())
        } answers {
            "d5:filesdee".toByteArray(charset)
        }
        every{
            httpMock.httpGET("http://legittorrents.info:2710/scrape",any())
        } answers {
            "d5:flagsd20:min_request_intervali900ee5:filesd20:infohashinfohashinfod8:completei778e10:incompletei1e10:downloadedi18241eeee".toByteArray(charset)
        }
        every{
            httpMock.httpGET("udp://62.138.0.158:6969/scrape",any())
        } answers {
            "d5:flagsd20:min_request_intervali900ee5:filesd20:infohashinfohashinfod8:completei74e10:incompletei12e10:downloadedi1241eeee".toByteArray(charset)
        }

        torrent.scrape(infohash)
        val oldScrape  = torrent.trackerStats(infohash)["http://legittorrents.info:2710"]

        every{
            httpMock.httpGET("http://legittorrents.info:2710/scrape",any())
        } answers {
            "d5:filesdee".toByteArray(charset)
        }
        torrent.scrape(infohash)
        assertThat(
            torrent.trackerStats(infohash)["http://legittorrents.info:2710"],
            equalTo(oldScrape).not()
        )
    }
    @Test
    fun `peer list grows on multiple announces`(){
        val infohash = torrent.load(shuffleableTorrent)
        every{
            httpMock.connectionSuccess
        } returns true
        every{
            httpMock.httpGET(any(),any())
        } answers {
            "d14:failure reason6:stupide".toByteArray(charset)
        }
        every{
            httpMock.httpGET("udp://tracker.opentrackr.org:1337/announce",any())
        } answers {
            "d8:intervali360e5:peers6:".toByteArray(charset) + byteArrayOf(127,0,0,22,26, 231.toByte()) + "e".toByteArray(charset)
        }
        torrent.announce(infohash, TorrentEvent.STARTED, 0, 0, 0)

        //Now this tracker no longer works, but we still want to keep the peer
        every{
            httpMock.httpGET("udp://tracker.opentrackr.org:1337/announce",any())
        } answers {
            "d14:failure reason6:stupide".toByteArray(charset)
        }
        every{
            httpMock.httpGET("udp://open.stealth.si:80/announce",any())
        } answers {
            "d8:intervali360e5:peers6:".toByteArray(charset) + byteArrayOf(127,0,0,21,26, 233.toByte()) + "e".toByteArray(charset)
        }

        torrent.announce(infohash, TorrentEvent.STARTED, 0, 0, 0)
        assertThat(
            torrent.knownPeers(infohash),
            anyElement(has(KnownPeer::ip, equalTo("127.0.0.22")) and has(KnownPeer::port, equalTo(6887)))
        )
        assertThat(
            torrent.knownPeers(infohash),
            anyElement(has(KnownPeer::ip, equalTo("127.0.0.21")) and has(KnownPeer::port, equalTo(6889)))
        )
    }
    @Test
    fun `all announces fail`(){
        val infohash = torrent.load(lame)
        every {
            httpMock.connectionSuccess
        } returns false
        every {
            httpMock.httpGET(any(),any())
        } answers {
            "connection fail"
        }
        assertThrows<TrackerException> { torrent.announce(infohash, TorrentEvent.STARTED,0,0,0) }
    }

}