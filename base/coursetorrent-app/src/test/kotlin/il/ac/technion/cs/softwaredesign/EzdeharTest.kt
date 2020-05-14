package il.ac.technion.cs.softwaredesign

import com.google.inject.Guice
import dev.misfitlabs.kotlinguice4.getInstance
import org.junit.jupiter.api.Test

class EzdeharTest {
    private val injector = Guice.createInjector(CourseTorrentTestModule())
    private val torrent = injector.getInstance<CourseTorrent>()
    private val debian = this::class.java.getResource("/debian-10.3.0-amd64-netinst.iso.torrent").readBytes()
//    private val lame = this::class.java.getResource("/lame.torrent").readBytes()
    @Test
    fun `after announce, client has up-to-date peer list`(){
        val infohash = torrent.load(debian)
        val p1= KnownPeer("127.0.0.22", 6887, "1")
        val p2= KnownPeer("127.0.0.21", 6889, "2")

    }
}