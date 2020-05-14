package il.ac.technion.cs.softwaredesign

import Utils.Conversion.toByteArray
import il.ac.technion.cs.softwaredesign.storage.SecureStorage
import il.ac.technion.cs.softwaredesign.storage.SecureStorageFactory

class SecureStorageFactoryImpl : SecureStorageFactory {

    private val storages = mutableMapOf<ByteArray, SecureStorage>()
    override fun open(name: ByteArray): SecureStorage{
        var storage = storages[name]
        if (storage == null) {
            storage = SecureStorage()
            storages[name] = storage
        }
        return storage
    }// implement our open function
}