package il.ac.technion.cs.softwaredesign

import org.junit.jupiter.api.Assertions
import java.lang.RuntimeException
import java.time.Duration
import org.junit.jupiter.api.function.ThrowingSupplier
import java.io.*
import java.util.stream.Collectors

fun <T> runWithTimeout(timeout: Duration, executable: () -> T): T =
    Assertions.assertTimeoutPreemptively(timeout, ThrowingSupplier(executable))

fun assertWithTimeout(executable : () -> Unit, timeout : Duration) : Unit =
    runWithTimeout(timeout, executable)

fun assertWithTimeout(executable : () -> Unit) = assertWithTimeout(executable, Duration.ofSeconds(10))

fun getPathOfFile(fileName: String): String {
    return object {}.javaClass.classLoader.getResource(fileName).path.substring(1)
}

class ServerManager
{

private val processes = ArrayList<Process>()
private val commandsParams = ArrayList<Pair<String, Int>>()
    /**
     * Runs the python server with the specified json file and port
     */
    fun runServer(jsonFile : String, port : Int)
    {
        execServer(jsonFile, port)
        commandsParams.add(Pair(jsonFile, port))
    }

    /**
     * Terminates all servers of current manager
     */
    fun closeAllServers()
    {
        processes.forEach{process->process.destroy()}
        processes.clear()
    }

    fun clearAll()
    {
        closeAllServers()
        commandsParams.clear()
    }

    fun restartAllServers()
    {
        closeAllServers()
        commandsParams.forEach{ command ->
           execServer(command.first, command.second)
        }
    }

    private fun getErrorOutput(proc:Process) : String
    {
        return BufferedReader(InputStreamReader(proc.errorStream)).lines().collect(Collectors.joining("\n"))
    }

    private fun execServer(jsonFile: String, port: Int)
    {
        val command = "python %s %s %d".format(getPathOfFile("torrent_http_server.py"),getPathOfFile(jsonFile), port)

        val process = Runtime.getRuntime().exec(command)
        if(!process.isAlive())
        {
            val error = getErrorOutput(process)
            throw RuntimeException("Command \"%s\" couldn't be executed\nError:%s".format(command,error))
        }
        processes.add(process)
    }

}

enum class Action(val value : Int)
{
    SCRAPE(0),
    ANNOUNCE(1);
    companion object{
        fun fromInt(value : Int) = Action.values().first { it.value == value }
    }
}

data class TorrEntry(val number : Int, val hash : String, val action : Action)
enum class TorrEntryCsvIdx(val idx : Int)
{
    NUMBER(0),
    HASH(1),
    ACTION(2)
}

object BigTest
{
    val fileName = "LibreOffice_6.3.6_Win_x64_helppack_en-US_0000.msi.torrent"
    val numberOfTorrents = 200
    val startIdxOfNumber = 402
    val padLength=4
}

fun makeIndexReplacement(original : ByteArray, idx : String) : ByteArray
{
    val newval = original
    for (i in (0..(idx.length-1)))
    {
        newval[BigTest.startIdxOfNumber + i] = idx[i].toByte()
    }
    return newval
}

fun loadTorrentsForBigTest(client : CourseTorrent) : Map<Int, String>
{
    val fileContents = File(getPathOfFile(BigTest.fileName)).readBytes()
    val idxToHashMap = HashMap<Int, String>()
    for (i in (0..(BigTest.numberOfTorrents-1)))
    {
        val idx = i.toString().padStart(BigTest.padLength, '0')
        val newContent = makeIndexReplacement(fileContents, idx)
        idxToHashMap.put(i,client.load(newContent))
    }

    return idxToHashMap
}

fun readOfficeCsv(fileName: String) : List<TorrEntry>
{
    val torrEntryData = ArrayList<TorrEntry>()
    var line : String?
    var fileReader = BufferedReader(FileReader(getPathOfFile(fileName)))
    fileReader.readLine()
    line=fileReader.readLine()
    while(line != null) {
        val tokens = line.split(',')
        if (tokens.size > 0) {
            val torrEntry = TorrEntry(
                number = tokens[TorrEntryCsvIdx.NUMBER.idx].toInt(),
                hash = tokens[TorrEntryCsvIdx.HASH.idx],
                action = Action.fromInt(tokens[TorrEntryCsvIdx.ACTION.idx].toInt())
            )
            torrEntryData.add(torrEntry)
        }
        line = fileReader.readLine()
    }
    return torrEntryData
}

fun executeActionsFromCsv(torrent : CourseTorrent, fileName: String, serverManager : ServerManager,restartEveryStep : Int = 10)
{
    val torrEntries = readOfficeCsv(fileName)
    var idx = 0
    torrEntries.forEach{
        if(it.action == Action.SCRAPE)
            torrent.scrape(it.hash)
        else
            torrent.announce(it.hash, TorrentEvent.REGULAR, 0 , 0, 0)
        idx++
        if(idx % restartEveryStep == 0)
        {
            serverManager.restartAllServers()
        }
    }

}