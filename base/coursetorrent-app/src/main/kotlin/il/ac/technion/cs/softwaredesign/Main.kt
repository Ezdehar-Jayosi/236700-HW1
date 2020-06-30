package il.ac.technion.cs.softwaredesign
/*
fun main() {
    println("hello, world")
}*/

/**
 * You can edit, run, and share this code.
 * play.kotlinlang.org
 */
import Utils.Bencoding
import Utils.Ben
import com.github.kittinunf.fuel.Fuel
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigestSpi
import java.security.MessageDigest
import java.net.HttpURLConnection
import java.io.*
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.result.*
import com.google.common.primitives.UnsignedBytes
import java.net.InetAddress
import java.util.HashMap

class ResourceGetter{
}
fun main() {
    val encoding = "UTF-8"
    val infohash = "5a8062c076fa85e8056451c0d9aa04349ae27909"
    val biptunia_infohash = "f9d7bb451668e10032d3a62ae91a9e8a723dd951"
    var request_params = URLEncoder.encode("info_hash", encoding) + "=" + Bencoding.urlInfohash(infohash)
    val IDsumHash = MessageDigest.getInstance("SHA-1").digest((315737809 + 313380164).toString().toByteArray())
    val IDsumHashPart = IDsumHash
        .map { i -> "%x".format(i) }
        .joinToString("")
        .take(6)
    val peer_id = "-CS1000-" + IDsumHashPart + "abcdef"
    val port = "6885"
    request_params += "&" + URLEncoder.encode("peer_id", encoding) + "=" + URLEncoder.encode(peer_id, encoding)
    request_params += "&" + URLEncoder.encode("port", encoding) + "=" + URLEncoder.encode(port, encoding)
    request_params += "&" + URLEncoder.encode("uploaded", encoding) + "=" + URLEncoder.encode("0", encoding)
    request_params += "&" + URLEncoder.encode("downloaded", encoding) + "=" + URLEncoder.encode("0", encoding)
    request_params += "&" + URLEncoder.encode("left", encoding) + "=" + URLEncoder.encode("0", encoding)
    request_params += "&" + URLEncoder.encode("compact", encoding) + "=" + URLEncoder.encode("1", encoding)
    request_params += "&" + URLEncoder.encode("event", encoding) + "=" + URLEncoder.encode("0", encoding)

    //var announce_list = listOf(listOf("http://legittorrents.info:2710/announce"))
    var announce_list = listOf(listOf("http://bttracker.debian.org:6969/scrape"))

    announce_list = announce_list.map { list -> list.shuffled(kotlin.random.Random(123)) }
    for (announce_tier in announce_list) {
        var good_announce: String? = null
        for (announce_url in announce_tier) {
            val req = "$announce_url?$request_params"
            val (request, response, result) = req.httpGet().response()
            when (result) {
                is Result.Failure -> {
                    val ex = result.getException()
                    print(ex)
                }
                is Result.Success -> {
                    val data = result.get()
                    println(data.toString(Charsets.UTF_8))
                    val announceResponse = Bencoding.DecodeObjectM(data) ?: throw IllegalArgumentException()
                    println(announceResponse)
                    /*val peers2: ByteArray = announceResponse["peers"] as ByteArray
                    println(peers2.toString())
                    if (false){//Bencoding.DecodeObject(peers2) is Map<*, *>) {
                        //println(peers2.toString())
                    } else {
                        val segmentedPeerList = peers2.asList().chunked(6)
                        for (portIP in segmentedPeerList) {
                            val peerIP = InetAddress.getByAddress(portIP.take(4).toByteArray()).hostAddress
                            val peerPort = ((portIP[4].toUByte().toInt() shl 8) + (portIP[5].toUByte().toInt()))
                            println("PortIP bytes: " + portIP as List<Int>)
                            println("Calculated IP: " + peerIP)
                            println("Port first byte: " + peerPort)
                            //println("Port second byte: " + peerPort2)
                        }
                        //println(knownPeersList)

                    }*/
                    //println(InetAddress.getByAddress((announceResponse["peers"].toString().toByteArray()).take(4).toByteArray()))
                    //val port_bytes = (announceResponse["peers"] as String).toByteArray()
                    //println(port_bytes.get(4).toInt()*256 + port_bytes.get(5).toInt())
                    //(0..7)
                    //    .forEach{println(announceResponse["peers"].toString().toByteArray().get(it).toInt())}
                }
            }

        }
    }
}