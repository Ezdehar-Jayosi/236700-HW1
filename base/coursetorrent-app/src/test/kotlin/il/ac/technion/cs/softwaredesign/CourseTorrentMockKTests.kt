package il.ac.technion.cs.softwaredesign


import com.google.inject.Guice
import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import dev.misfitlabs.kotlinguice4.getInstance
//import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import java.util.*
import kotlin.collections.HashMap

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class CourseTorrentMockKTests {
    private val injector = Guice.createInjector(CourseTorrentModule())
    private val torrent = injector.getInstance<CourseTorrent>()
    private val debian = this::class.java.getResource("/debian-10.3.0-amd64-netinst.iso.torrent").readBytes()
    private val lame = this::class.java.getResource("/lame.torrent").readBytes()
    private val announceListTorrent = this::class.java.getResource("/Legal torrent of BipTunia's 38th album “DON2019T CARE” - FLAC 24-bit lossless.torrent").readBytes()
    private val largerTorrent = this::class.java.getResource("/PublicDomainTorrents.info Backup of ALL Torrents [12-17-2016].torrent").readBytes()
    private val charset = Charsets.UTF_8

    @BeforeEach
    fun setUpMockk() {
        val Storage = HashMap<String?, ByteArray?>()
        //mockkStatic("il.ac.technion.cs.softwaredesign.storage.SecureStorage")
        mockk<SecureStorage>(relaxed = true)
        val key_slot = slot<ByteArray>()
        val val_slot = slot<ByteArray>()
        every {
            write(capture(key_slot), capture(val_slot))
        } answers {
            val value = val_slot.captured
            val key = key_slot.captured.toString(charset)
            Storage[key] = value
        }
        every {
            read(capture(key_slot))
        } answers {
            val key = key_slot.captured.toString(charset)
            Storage[key]
        }
    }
    @Test
    fun `after load, infohash calculated correctly`() {
        val torrent = CourseTorrent()
        val infohash = torrent.load(debian)
        assertThat(infohash, equalTo("5a8062c076fa85e8056451c0d9aa04349ae27909"))
    }
    @Test
    fun `unloading or announcing a torrent that hasn't been loaded fails`() {
        val torrent = CourseTorrent()
        assertThrows<IllegalArgumentException> { torrent.unload("5a8062c076fa85e8056451c0d9aa04349ae27909") }
        assertThrows<IllegalArgumentException> { torrent.announces("5a8062c076fa85e8056451c0d9aa04349ae27909") }
    }
    @Test
    fun `announce on unloaded torrent`() {
        val torrent = CourseTorrent()
        val infohash = torrent.load(debian)
        torrent.unload(infohash)
        assertThrows<IllegalArgumentException> { torrent.announces(infohash) }
    }
    @Test
    fun `loading, unloading and reloading torrent`() {
        val torrent = CourseTorrent()
        val infohash = torrent.load(debian)
        torrent.unload(infohash)
        torrent.load(debian)
    }
    @Test
    fun `failing on loading the same torrent twice`() {
        val torrent = CourseTorrent()
        torrent.load(debian)
        assertThrows<IllegalStateException> { torrent.load(debian) }
    }
    @Test
    fun `test correctness of announce`() {
        val torrent = CourseTorrent()
        val infohash = torrent.load(debian)
        val announces = torrent.announces(infohash)
        assertThat(announces, allElements(hasSize(equalTo(1))))
        assertThat(announces, hasSize(equalTo(1)))
        assertThat(announces, allElements(hasElement("http://bttracker.debian.org:6969/announce")))
    }
    @Test
    fun `test correctness of announce-list`() {
        val torrent = CourseTorrent()
        val infohash = torrent.load(announceListTorrent)
        val announces = torrent.announces(infohash)
        assertThat(announces, allElements(hasSize(equalTo(1))))
        assertThat(announces, hasSize(equalTo(8)))
        assertThat(announces, anyElement(hasElement("udp://62.138.0.158:6969/announce")))
    }
    @Test
    fun `loading multiple torrent and reading their announces`() {
        val torrent = CourseTorrent()
        val infohash1 = torrent.load(debian)
        val infohash2 = torrent.load(announceListTorrent)
        val infohash3 = torrent.load(largerTorrent)
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
        assertThat(announces3, allElements(hasElement("http://legittorrents.info:2710/announce")))
    }
    @Test
    fun `loading, unloading and announcing for multiple torrents`(){
        val torrent = CourseTorrent()
        val infohash2 = torrent.load(announceListTorrent)
        val infohash3 = torrent.load(largerTorrent)
        torrent.unload(infohash2)
        assertThrows<IllegalArgumentException> {   torrent.announces(infohash2)}
        torrent.load(announceListTorrent)
        val announces2 = torrent.announces(infohash2)
        //Loading a different torrent to check that loading the new torrent doesn't affect previous torrents
        torrent.load(debian)
        //assert that the announce remains for the old torrent
        assertThat(announces2, allElements(hasSize(equalTo(1))))
        assertThat(announces2, hasSize(equalTo(8)))
        assertThat(announces2, anyElement(hasElement("udp://62.138.0.158:6969/announce")))


    }

}