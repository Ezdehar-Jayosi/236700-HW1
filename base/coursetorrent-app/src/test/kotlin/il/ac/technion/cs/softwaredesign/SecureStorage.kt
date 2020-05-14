package il.ac.technion.cs.softwaredesign

import il.ac.technion.cs.softwaredesign.storage.SecureStorage

class SecureStorage : SecureStorage {

     private val storage =  mutableMapOf<ByteArray,ByteArray>()

    override fun read(key: ByteArray): ByteArray? {  //not final implementation just to check if this works
        return storage[key]
    }

    override fun write(key: ByteArray, value: ByteArray) {
        storage.put(key,value)
    }
}
