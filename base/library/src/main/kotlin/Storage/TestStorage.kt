package Storage

import il.ac.technion.cs.softwaredesign.storage.SecureStorage


class SecureStorageImpl  : SecureStorage {

    val storage = mutableMapOf<String,ByteArray>()

    private val enc = Charsets.UTF_8
    override fun read(key: ByteArray): ByteArray? {
        val value = storage[key.toString(enc)]
        if (value != null) {
            Thread.sleep(value.size.toLong())
        }
        return value
    }

    override fun write(key: ByteArray, value: ByteArray) {
        storage[key.toString(enc)] = value
    }
}
