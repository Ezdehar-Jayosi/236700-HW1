package il.ac.technion.cs.softwaredesign


import Utils.Bencoding
import Utils.HTTPGet
import com.google.inject.Guice
import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import dev.misfitlabs.kotlinguice4.getInstance
import io.mockk.*
//import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import io.mockk.*
import org.junit.jupiter.api.*
import java.util.*
import kotlin.collections.HashMap

//@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class CourseTorrentTests {
    private val injector = Guice.createInjector(CourseTorrentModule())
    private val torrent = injector.getInstance<CourseTorrent>()
    private val debian = this::class.java.getResource("/debian-10.3.0-amd64-netinst.iso.torrent").readBytes()
    private val lame = this::class.java.getResource("/lame.torrent").readBytes()
    private val announceListTorrent = this::class.java.getResource("/Legal torrent of BipTunia's 38th album “DON2019T CARE” - FLAC 24-bit lossless.torrent").readBytes()
    //private val largerTorrent = this::class.java.getResource("/PublicDomainTorrents.info Backup of ALL Torrents [12-17-2016].torrent").readBytes()
    private val charset = Charsets.UTF_8
    //private val httpMock = mockk<HTTPGet>(relaxed=true)
    /*@BeforeEach
    fun setUpMockk() {
        val Storage = HashMap<String?, ByteArray?>()
        //mockkStatic("il.ac.technion.cs.softwaredesign.storage.SecureStorage")
        //mockk<SecureStorage>(relaxed = true)
        val key_slot = slot<ByteArray>()
        val val_slot = slot<ByteArray>()
        every {
            //write(capture(key_slot), capture(val_slot))
        } answers {
            val value = val_slot.captured
            val key = key_slot.captured.toString(charset)
            Storage[key] = value
        }
        every {
            //read(capture(key_slot))
        } answers {
            val key = key_slot.captured.toString(charset)
            Storage[key]
        }
    }*/

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
    }
    @Test
    fun `failing on loading the same torrent twice`() {
        torrent.load(debian)
        assertThrows<IllegalStateException> { torrent.load(debian) }
    }
    @Test
    fun `test correctness of announce`() {
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
        val httpMock = mockk<HTTPGet>(relaxed=false)
        every{
            httpMock.httpGET(any(),any())
        } answers{ "d8:intervali360e5:peers0:e".toByteArray(charset) }
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
        //every{
          //  httpMock.httpGET("https://127.0.0.1:8082/announce",any())
        //} returns 0
        /* Tracker has infohash, 0 complete, 0 downloaded, 0 incomplete, no name key */
        assertDoesNotThrow { torrent.scrape(infohash) }

        assertThat(
            torrent.trackerStats(infohash),
            equalTo(mapOf(Pair("http://127.0.0.1:8082", Scrape(0, 0, 0, null) as ScrapeData)))
        )
        /* Assertion to verify that the tracker was actually called */
    }
    @Test
    fun `new methods fail on unloaded torrent`(){

    }
    @Test
    fun `invalidate unit test`(){
        val infohash = torrent.load(lame)
        /* Returned peer list is: [("127.0.0.22", 6887)] */
        torrent.announce(infohash, TorrentEvent.STARTED, 0, 0, 2703360)
        /* Returned peer list is: [("127.0.0.22", 6887), ("127.0.0.21", 6889)] */
        torrent.announce(infohash, TorrentEvent.REGULAR, 0, 81920, 2621440)

        torrent.invalidatePeer(infohash, KnownPeer("127.0.0.22", 6887, null))

        assertThat(
            torrent.knownPeers(infohash),
            anyElement(has(KnownPeer::ip, equalTo("127.0.0.21")) and has(KnownPeer::port, equalTo(6889))).not()
        )
    }
    @Test
    fun `peer list after announce`(){
        val infohash = torrent.load(lame)

        /* Returned peer list is: [("127.0.0.22", 6887)] */
        torrent.announce(infohash, TorrentEvent.STARTED, 0, 0, 2703360)
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
    fun `b`(){

    }
    @Test
    fun `c`(){

    }
    @Test
    fun `d`(){

    }
    @Test
    fun `e`(){

    }


}