package Storage

import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import il.ac.technion.cs.softwaredesign.storage.SecureStorageFactory

class SecureStorageFactoryImpl : SecureStorageFactory {
    private val storageMap = mutableMapOf<String, SecureStorage>()
    private val enc = Charsets.UTF_8
    override fun open(name: ByteArray): SecureStorage{
        val storage = storageMap[name.toString(enc)]
        var returnVal : SecureStorage? = null
        if (storage != null)
            returnVal = storage
        else
            returnVal = SecureStorageImpl()
            storageMap[name.toString(enc)] = returnVal

        return returnVal
    }
}