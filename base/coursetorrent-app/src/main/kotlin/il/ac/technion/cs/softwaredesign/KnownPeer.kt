package il.ac.technion.cs.softwaredesign

import java.io.Serializable

data class KnownPeer (
    val ip: String,
    val port: Int,
    val peerId: String?
): Serializable