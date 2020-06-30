package il.ac.technion.cs.softwaredesign

import com.google.inject.Guice
import com.natpryce.hamkrest.*
import com.natpryce.hamkrest.assertion.assertThat
import dev.misfitlabs.kotlinguice4.getInstance
import il.ac.technion.cs.softwaredesign.exceptions.TrackerException
import org.junit.jupiter.api.*


class CourseTorrentStaffTest {
    private val injector = Guice.createInjector(CourseTorrentModule())
    private val serverManager = ServerManager()
    private var torrent = injector.getInstance<CourseTorrent>()

    private val libreo = this::class.java.getResource("/LibreOffice_6.3.6_helppack2.msi.torrent").readBytes()
    private val slack = this::class.java.getResource("/Slackware64_15.1.torrent").readBytes()
    private val debian = this::class.java.getResource("/debian-10.3.0-i386-netinst_2.iso.torrent").readBytes()

	protected fun finalize()
    {
        serverManager.clearAll()
    }
	
    @BeforeEach
    fun setup() {
        torrent = injector.getInstance<CourseTorrent>()
    }

    @AfterEach
    fun clear() {
        serverManager.clearAll()
    }

    @Nested
    inner class SmallTest {
        @Test
        fun `announce for unloaded info hash`() {
            val infohash = torrent.load(libreo)
            torrent.unload(infohash)
            assertWithTimeout {
                assertThrows<IllegalArgumentException> { torrent.announce(infohash, TorrentEvent.STARTED, 0, 0, 0) }

            }
        }

        @Test
        fun `announce for a torrent with single tracker`() {
            val infohash = torrent.load(libreo)
            serverManager.runServer("basic_server_0.json", 6969)
            assertWithTimeout {
                val retry = torrent.announce(infohash, TorrentEvent.STARTED, 0, 0, 0)
                assertThat(retry, equalTo(120))
            }
        }

        @Test
        fun `announce for a torrent when tracker returns error`() {
            val infohash = torrent.load(libreo)
            serverManager.runServer("basic_server_1.json", 6969)

            assertWithTimeout {
                var ex = assertThrows<TrackerException> { torrent.announce(infohash, TorrentEvent.REGULAR, 0, 0, 0) }
                assertThat(ex.message, equalTo("The first error"))
                ex = assertThrows<TrackerException> { torrent.announce(infohash, TorrentEvent.REGULAR, 0, 0, 0) }
                assertThat(ex.message, equalTo("The last error"))
            }
        }

        @Test
        fun `scrape for unloaded info hash`() {
            val infohash = torrent.load(slack)
            torrent.unload(infohash)
            assertWithTimeout {
                assertThrows<IllegalArgumentException> { torrent.scrape(infohash) }
            }
        }

        @Test
        fun `scrape for a torrent with a single tracker`() {
            val infohash = torrent.load(slack)
            serverManager.runServer("basic_server_1.json", 6968)
            assertWithTimeout {

                torrent.scrape(infohash)
                var trackStats = torrent.trackerStats(infohash)
                assertThat(
                    trackStats["http://127.0.0.1:6968/announce"],
                    present(equalTo(Scrape(60, 12, 10, null) as ScrapeData))
                )
                torrent.scrape(infohash)
                trackStats = torrent.trackerStats(infohash)
                assertThat(
                    trackStats["http://127.0.0.1:6968/announce"],
                    present(equalTo(Scrape(50, 0, 0, "Slackware.bin") as ScrapeData))
                )

            }
        }

        @Test
        fun `scrape for a torrent with error response`() {
            val infohash = torrent.load(libreo)
            serverManager.runServer("basic_server_1.json", 6969)

            assertWithTimeout {
                torrent.scrape(infohash)
                var trackStats = torrent.trackerStats(infohash)
                assertThat(
                    trackStats["http://127.0.0.1:6969/announce"],
                    present(isA<Failure>()))
                torrent.scrape(infohash)
                trackStats = torrent.trackerStats(infohash)
                assertThat(
                    trackStats["http://127.0.0.1:6969/announce"],
                    present(isA<Failure>()))
            }
        }

        @Test
        fun `scrape for a torrent whose connection failed`()
        {
            val infohash = torrent.load(libreo)

            assertWithTimeout {
                torrent.scrape(infohash)
                val trackStats = torrent.trackerStats(infohash)
                assertThat(
                    trackStats["http://127.0.0.1:6969/announce"],
                    present(isA<Failure>()))
            }
        }

        @Test
        fun `trackerStats for unloaded torrent`()
        {
            val infohash = torrent.load(slack)
            torrent.unload(infohash)
            assertWithTimeout {
                assertThrows<IllegalArgumentException> { torrent.trackerStats(infohash) }
            }
        }

        @Test
        fun `knownPeers for non existent hash`() {
            val infohash = torrent.load(slack)
            torrent.unload(infohash)
            assertWithTimeout {
                assertThrows<IllegalArgumentException> { torrent.knownPeers(infohash) }
            }
        }

        @Test
        fun `knownPeers returns a sorted list`() {
            val infohash = torrent.load(slack)
            serverManager.runServer("basic_server_1.json", 6968)
            assertWithTimeout {
                torrent.announce(infohash, TorrentEvent.STARTED, 0, 0, 0)
                var peers = torrent.knownPeers(infohash)
                assertThat(
                    peers, equalTo(
                        listOf(
                            KnownPeer("33.27.224.54", 45110, null),
                            KnownPeer("48.103.54.228", 45340, null),
                            KnownPeer("85.150.95.249", 30034, null),
                            KnownPeer("144.236.197.75", 59514, null),
                            KnownPeer("171.146.119.25", 20212, null),
                            KnownPeer("191.93.149.147", 32117, null),
                            KnownPeer("224.0.156.4", 32159, null),
                            KnownPeer("243.35.24.105", 24648, null)
                        )
                    )
                )
                torrent.announce(infohash, TorrentEvent.STARTED, 0, 0, 0)
                peers = torrent.knownPeers(infohash)
                assertThat(
                    peers, equalTo(
                        listOf(
                            KnownPeer("33.27.224.54", 45110, null),
                            KnownPeer("48.103.54.228", 45340, null),
                            KnownPeer("80.79.128.22", 12555, null),
                            KnownPeer("80.80.127.22", 31444, null),
                            KnownPeer("80.80.128.4", 12401, null),
                            KnownPeer("80.80.128.5", 12400, null),
                            KnownPeer("80.80.128.22", 12555, null),
                            KnownPeer("80.80.128.37", 13405, null),
                            KnownPeer("80.80.128.111", 21900, null),
                            KnownPeer("85.150.95.249", 30034, null),
                            KnownPeer("144.236.197.75", 59514, null),
                            KnownPeer("171.146.119.25", 20212, null),
                            KnownPeer("191.93.149.147", 32117, null),
                            KnownPeer("224.0.156.4", 32159, null),
                            KnownPeer("243.35.24.105", 24648, null)
                        )
                    )
                )
            }
        }

        @Test
        fun `invalidate peer for non existent torrent`()
        {
            val infohash = torrent.load(slack)
            torrent.unload(infohash)
            assertWithTimeout {
                assertThrows<IllegalArgumentException> { torrent.invalidatePeer(infohash, KnownPeer("85.150.95.249", 30034, null)) }

            }
        }
        @Test
        fun `invalidate peer removes peers`() {
            val infohash = torrent.load(slack)
            serverManager.runServer("basic_server_1.json", 6968)
            assertWithTimeout {
                torrent.announce(infohash, TorrentEvent.STARTED, 0, 0, 0)
                torrent.invalidatePeer(infohash, KnownPeer("85.150.95.249", 30034, null))
                torrent.invalidatePeer(infohash, KnownPeer("85.150.95.14", 30034, null))
                torrent.invalidatePeer(infohash, KnownPeer("171.146.119.25", 20212, null))

                val peers = torrent.knownPeers(infohash)
		assertThat(
		    peers, Matcher.Negation(
	                hasElement(KnownPeer("85.150.95.249", 30034, null))))
		assertThat(
		    peers, Matcher.Negation(
	                hasElement(KnownPeer("85.150.95.14", 30034, null))))
		assertThat(
		    peers, Matcher.Negation(
	                hasElement(KnownPeer("171.146.119.25", 20212, null))))
            }

        }

        @Test
        fun `invalidate peer removes peers when there are peers with same ip but different ports`()
        {
            val infohash = torrent.load(slack)
            serverManager.runServer("basic_server_2.json", 6968)
            assertWithTimeout {
                torrent.announce(infohash, TorrentEvent.STARTED, 0, 0, 0)
                torrent.invalidatePeer(infohash, KnownPeer("80.80.128.5", 12401, null))
                var peers = torrent.knownPeers(infohash)
                assertThat(
                    peers, equalTo(
                        listOf(KnownPeer("80.80.128.5", 12400, null),
                            KnownPeer("80.80.128.8", 12402, null),
                            KnownPeer("80.80.128.22", 12555, null))))
                torrent.announce(infohash, TorrentEvent.STARTED, 0, 0, 0)
                torrent.invalidatePeer(infohash, KnownPeer("80.80.128.5", 12400, null))
                peers = torrent.knownPeers(infohash)
                assertThat(
                    peers, equalTo(
                        listOf(KnownPeer("80.80.128.5", 12401, null),
                            KnownPeer("80.80.128.8", 12402, null),
                            KnownPeer("80.80.128.22", 12555, null))))
            }

        }
    }

    @Nested
    inner class MediumTest {

        @Test
        fun `announce with multiple trackers with shuffle`()
        {
            val infohash = torrent.load(debian)
            serverManager.runServer("deb_server_0.json", 6967)
            serverManager.runServer("deb_server_1.json", 6968)
            serverManager.runServer("deb_server_2.json", 6969)
            assertWithTimeout {
                val listBefore = torrent.announces(infohash).get(0)
                torrent.announce(infohash, TorrentEvent.STARTED, 0 ,0 ,0)
                val listAfter = torrent.announces(infohash).get(0)

                assertThat(listBefore, Matcher.Negation(equalTo(listAfter)))

            }
        }

        @Test
        fun `scrape with multiple trackers`()
        {
            val infohash = torrent.load(debian)
            serverManager.runServer("deb_server_0.json", 6967)
            serverManager.runServer("deb_server_1.json", 6968)

            assertWithTimeout {
                torrent.scrape(infohash)
                val trackInfo = torrent.trackerStats(infohash)
                assertThat(trackInfo["http://127.0.0.1:6967/announce"], equalTo(Scrape(194, 76, 86, "Debian_oMehN.bin") as ScrapeData))
                assertThat(trackInfo["http://127.0.0.1:6968/announce"], equalTo(Scrape(69, 73, 34, "Debian_K2ECW.bin") as ScrapeData))
                assertThat(trackInfo["http://127.0.0.1:6969/announce"], present(isA<Failure>()))

            }
        }
    }

    @Nested
    inner class BigTest {
        @Test
        fun `200 torrents with uniformly distributed announce and scrape calls`()
        {
            val infoHashes = loadTorrentsForBigTest(torrent)
            serverManager.runServer("big_server_0.json", 6969)
            serverManager.runServer("big_server_1.json", 6967)
            serverManager.runServer("big_server_2.json", 6968)
            executeActionsFromCsv(torrent, "offices.csv", serverManager)

            assertWithTimeout {
                val knownPeers=torrent.knownPeers(infoHashes[22] as String)
                assertThat(knownPeers, equalTo(listOf(KnownPeer("67.238.172.38", 59786, null),
                    KnownPeer("154.110.219.236", 37574, null),
                    KnownPeer("183.78.161.225", 36182, null),
                    KnownPeer("251.32.174.67", 27864, null))))
                val stats = torrent.trackerStats(infoHashes[130] as String)
                assertThat(stats["http://127.0.0.2:6967/announce"], equalTo(Scrape(37, 39, 83, null) as ScrapeData))
                assertThat(stats["http://127.0.0.2:6969/announce"], equalTo(Failure("dMljzR") as ScrapeData))


            }

        }
    }
}
