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
import java.util.HashMap

class ResourceGetter{
}
fun main() {
    val encoding = "UTF-8"
    val infohash = "5a8062c076fa85e8056451c0d9aa04349ae27909"
    //val infohash = "821ca5cfbb92f008f6f5ecfd2a894f35deb41d56" //biptunia hash
    var request_params = URLEncoder.encode("info_hash",encoding) + "=" + Bencoding.urlInfohash(infohash)
    val IDsumHash = MessageDigest.getInstance("SHA-1").digest((315737809+313380164).toString().toByteArray())
    val IDsumHashPart = IDsumHash
        .map{i->"%x".format(i)}
        .joinToString("")
        .take(6)
    val peer_id = "-CS1000-"+IDsumHashPart+"abcdef"
    val port = "6885"
    request_params += "&" + URLEncoder.encode("peer_id",encoding) +"="+URLEncoder.encode(peer_id,encoding)
    request_params += "&" + URLEncoder.encode("port",encoding) +"="+URLEncoder.encode(port,encoding)
    request_params += "&" + URLEncoder.encode("uploaded",encoding) +"="+URLEncoder.encode("0",encoding)
    request_params += "&" + URLEncoder.encode("downloaded",encoding) +"="+URLEncoder.encode("0",encoding)
    request_params += "&" + URLEncoder.encode("left",encoding) +"="+URLEncoder.encode("0",encoding)
    request_params += "&" + URLEncoder.encode("compact",encoding) +"="+URLEncoder.encode("1",encoding)
    request_params += "&" + URLEncoder.encode("event",encoding) +"="+URLEncoder.encode("0",encoding)

    var announce_list = listOf(listOf("http://bttracker.debian.org:6969/announce"))
    announce_list = announce_list.map { list -> list.shuffled(kotlin.random.Random(123)) }
    for(announce_tier in announce_list) {
        var good_announce : String? = null
        for (announce_url in announce_tier) {
            val req = "$announce_url?$request_params"
            val (request, response, result) = req.httpGet().response()
            when (result) {
                is Result.Failure -> {
                    val ex = result.getException()
                    throw IllegalArgumentException() //TODO: replace with something else? not a tracker failure
                }
                is Result.Success -> {
                    val data = result.get()
                    val announceResponse = Bencoding.DecodeObjectM(data) ?: throw IllegalArgumentException()
                    print(announceResponse)
                }
            }

        }
    }
}