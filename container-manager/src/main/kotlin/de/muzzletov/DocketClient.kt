package de.muzzletov

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import de.muzzletov.model.*
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.nio.channels.spi.SelectorProvider
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object DocketClient : Runnable {
    private var dataCallback: DataCallback? = null
    private lateinit var selector: Selector
    private lateinit var socketChannel: SocketChannel
    private val mapper = jacksonObjectMapper()
    private var request: ByteBuffer? = null
    private var running = false
    private var parseData = ParseData()
    private val lock: Lock = ReentrantLock()
    private val clientlock: Lock = ReentrantLock()
    private val waitrequest = lock.newCondition()
    private val waitresponse = lock.newCondition()
    private val waitclient = clientlock.newCondition()
    private const val printHeader = false
    private var written = 0
    private var thread: Thread? = null
    private val stringBuilder = StringBuilder("")
    private val headerHandler: HeaderHandler = HeaderHandler()
    private val chunkedHandler: ChunkedBodyHandler = ChunkedBodyHandler()
    private val fixedHandler: FixedBodyHandler = FixedBodyHandler()
    private val finalizeHandler: FinalizeHandler = FinalizeHandler()
    private val readBuffer: ByteBuffer = ByteBuffer.allocate(4096)

    private var handler: Handler = headerHandler
    private val pattern = arrayOf('\r', '\n')

    class FixedBodyHandler: Handler {
        override fun handle(data: String): Boolean {
            stringBuilder.append(data)
            parseData.contentLength = parseData.contentLength?.minus(data.length)
            val done = parseData.contentLength == 0

            if(done) {
                cleanup()
            }

            return done
        }
    }

    class FinalizeHandler: Handler {
        override fun handle(data: String): Boolean {
            var i = 0
            var index = 0

            while(data.length > i) {
                if (data[i] == pattern[index]) {
                    index = (index + 1) % pattern.size
                    if (index == 0) parseData.count++
                }

                if (parseData.count == 1) {
                    cleanup()
                    return true
                }
                i++
            }

            return false
        }
    }

    fun cleanup() {
        handler = headerHandler
        parseData = ParseData()
        dataCallback = null
    }

    class ChunkedBodyHandler: Handler {
        override fun handle(data: String): Boolean {
            var i = 0
            var index = 0
            parseData.lastval = 0

            while (data.length > i) {
                if(parseData.chunkLength !=null) {

                    if(parseData.chunkLength!! != 0) {
                        parseData.chunkLength=parseData.chunkLength!!-1
                        i++
                        continue
                    }

                    if(dataCallback?.enough(parseData.chunkData.toString()+data.subSequence(parseData.lastval, i ).toString()) == true) {
                        cleanup()
                        return true
                    }

                    stringBuilder.append(parseData.chunkData)
                    stringBuilder.append(data.subSequence(parseData.lastval, i ))

                    parseData.lastval = i + 2
                    parseData.chunkLength = null
                    parseData.count = 0

                    parseData.chunkData.clear()
                }

                if (data[i] == pattern[index]) {
                    index = (index + 1) % pattern.size
                    if (index == 0) parseData.count++
                } else {
                    parseData.count = 0
                    index = 0
                }

                if (parseData.count == 1 && parseData.lastval <= i) {
                    if(parseData.lastval == i) {
                        return true
                    }

                    parseData.chunkLength = Integer.decode(
                        "0x${parseData.chunkData}${data.subSequence(parseData.lastval, i - 1)}"
                    )

                    if(parseData.chunkLength == 0) {
                        handler = finalizeHandler
                        return finalizeHandler.handle(data.subSequence(i, data.length).toString())
                    }

                    parseData.lastval = i + 1
                    parseData.chunkData.clear()

                }

                i++
            }

            if(parseData.lastval < data.length - 1) {
                parseData.chunkData.append(data.subSequence(parseData.lastval, data.length))
            }

            return false
        }
    }
    
    class HeaderHandler: Handler {
        override fun handle(data: String): Boolean {
            var i = 0
            var index = 0

            parseData.lastval = 0

            while(data.length > i) {
                if(data[i] == pattern[index]) {
                    index = (index+1)%pattern.size
                    if(index == 0) parseData.count++
                } else {
                    parseData.masked = false
                    parseData.count = 0
                    index = 0
                }

                if (!parseData.masked && parseData.count == 1) {
                    val header = stringBuilder.append(data.subSequence(parseData.lastval, i - 1).toString()).toString()
                    if(printHeader)
                        ContainerManager.log(header)
                    if (header.lowercase().startsWith("content-length")) {
                        parseData.contentLength = Integer.decode(header.split(":")[1].trim())
                    } else if (header.lowercase().startsWith("transfer-encoding") && header.lowercase().endsWith("chunked")) {
                        parseData.chunked = true
                    }

                    stringBuilder.clear()

                    parseData.lastval = i + 1
                    parseData.masked = true
                }

                if(parseData.count == 2) {
                    parseData.contentPart = true
                    parseData.masked = false
                    stringBuilder.clear()
                    i++
                    break
                }

                i++
            }

            if(!parseData.contentPart && i-1 > parseData.lastval) {
                stringBuilder.append(data.subSequence(parseData.lastval, i))
            }

            if(parseData.contentPart) {
                if (parseData.contentLength == null && !parseData.chunked) return true
                handler = if(parseData.chunked) chunkedHandler else fixedHandler
                return handler.handle(data.subSequence(i, data.length).toString())
            }

            return false
        }
    }


    init {
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        Runtime.getRuntime().addShutdownHook(Thread {
            run() {
                thread?.interrupt()
            }
        })
    }

    private fun start(): DocketClient {
        thread = Thread(this)
        thread!!.start()
        return this
    }

    private fun add(request: String, callback: DataCallback? = null): String {
        return add(ByteBuffer.wrap(request.toByteArray()), callback)
    }

    private fun add(request: ByteBuffer, dataCallback: DataCallback? = null): String {
        clientlock.withLock {
            lock.withLock {

                if (!running)
                    this.start()
                this.dataCallback = dataCallback
                this.request = request

                waitrequest.signal()
                waitresponse.await()

                val response = stringBuilder.toString()

                waitclient.signal()

                return response
            }
        }
    }
    
    private fun processConnect(key: SelectionKey): Boolean {
        val socketChannel: SocketChannel = key.channel() as SocketChannel
        try {
            while (socketChannel.isConnectionPending) {
                socketChannel.finishConnect();
            }
        } catch (e: IOException) {
            key.cancel();
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private fun processReadySet(selector: Selector): Boolean {
        val iterator = selector.selectedKeys().iterator()

        while (iterator.hasNext()) {

            val key: SelectionKey = iterator.next()

            if (key.isConnectable) {
                val connected: Boolean = processConnect(key);
                if (!connected) {
                    running = false
                    return false
                }
            }

            if (key.isWritable
                && request != null
                && written < request!!.limit()) {
                request!!.position(written)
                val socketChannel: SocketChannel = key.channel() as SocketChannel
                written += socketChannel.write(request)
            }

            if (key.isReadable) {
                val socketChannel: SocketChannel = key.channel() as SocketChannel
                val read = socketChannel.read(readBuffer)

                if(read == -1) {
                    key.channel().close()
                    key.cancel()
                    running = false
                    return false
                }

                if(handler.handle(String(readBuffer.array().copyOfRange(0, read))))
                    return false

                readBuffer.position(0)
            }

            iterator.remove()
        }

        return true;
    }

    private fun initSocket() {

        selector = SelectorProvider.provider().openSelector()
        socketChannel = SocketChannel.open(StandardProtocolFamily.UNIX)

        socketChannel.connect(UnixDomainSocketAddress.of("/var/run/docker.sock"))
        socketChannel.configureBlocking(false)
        socketChannel.register(
            selector,
            SelectionKey.OP_CONNECT or SelectionKey.OP_READ or SelectionKey.OP_WRITE
        )

        running = true
    }
    override fun run() {

        while (!Thread.interrupted()) {
            try {
                if(!running)
                    initSocket()
                lock.withLock {
                    if (request == null) {
                        waitrequest.await()
                    }

                    stringBuilder.clear()
                    written = 0

                    do {
                        selector.select()
                        Thread.sleep(30)
                    } while (processReadySet(selector))

                    parseData = ParseData()
                    request = null
                    waitresponse.signal()
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    private fun getAll(identifier: String): String = add(get(identifier))

    fun getAllImages(): ArrayList<Image> = mapper.readValue(getAll("images"), object: TypeReference<ArrayList<Image>>() {})
    fun getAllContainers(): ArrayList<Container> = mapper.readValue(getAll("containers"), object: TypeReference<ArrayList<Container>>() {})

    private fun post(data: ByteArray? = null, endpoint: String, options: String = "/json", contenttype: String? = "application/json"): ByteBuffer {
        val headers = HashMap<String, String>()

        headers["POST"] = "/$endpoint$options HTTP/1.1"
        headers["Host:"] = "localhost"
        contenttype?.let { headers["Content-Type:"] = contenttype }
        data?.let { headers["Content-Length:"] = data.size.toString() }

        val header = "${headers.map { (key, value)->"$key $value" }.joinToString ("\r\n" )}\r\n\r\n"
        val byteBuffer = ByteBuffer.allocate(header.length + (data?.size?:0))

        byteBuffer.put(0, header.toByteArray())
        data?.let { byteBuffer.put(header.length, data) }

        return byteBuffer
    }

    fun get(endpoint: String, options: String = "/json?all=true"): String {
        val headers = HashMap<String, String>()

        headers["GET"] = "/$endpoint$options HTTP/1.1"
        headers["Host:"] = "localhost"

        return "${headers.map { (key, value) -> "$key $value" }.joinToString("\r\n")}\r\n\r\n"
    }

    fun buildImage(image: ImageOut): String? {
        ContainerManager.log("Building image ${image.tags}")
        val outputStream = ByteArrayOutputStream()
        val out = TarArchiveOutputStream(outputStream)

        getFiles(image.path)?.forEach{
            val entry = TarArchiveEntry(it!!.name)
            entry.size = it.length()
            out.putArchiveEntry(entry)
            out.write(it.readBytes())
            out.closeArchiveEntry()
        }

        val payload: ByteArray = outputStream.toByteArray()

        outputStream.close()
        out.close()
        val buildCallback = BuildCallback()

        add(
            post(
                payload, endpoint = "build", options = "?${image.tags.joinToString ( "&", transform = { "t=$it" } )}", contenttype = "application/octet-stream"
            ), buildCallback
        )

        return buildCallback.getImageId()
    }

    class BuildCallback : DataCallback() {
        var id: String? = null
        fun getImageId(): String? {
            return id
        }
        override fun enough(data: String): Boolean {
            val obj = JSONObject(data)

            if(obj.has("aux")) {
                id = obj.getJSONObject("aux").getString("ID")
            } else if (obj.has("stream")) {
                print(obj.getString("stream"))
            }

            return false
        }
    }

    private fun isChunked(header: String): Boolean {
        return header.split("\r\n").find { it.trim().lowercase().startsWith("transfer-encoding") }?.lowercase()?.endsWith("chunked")?:false
    }

    private fun getFiles(dir: String): Set<File?>? {
        return File(dir.substring(0, dir.lastIndexOf(File.separator))).listFiles()
            ?.filter { file -> !file.isDirectory }?.toSet()
    }

    class StatsCallback: DataCallback() {
        var data: ByteBuffer = ByteBuffer.allocate(1024)
        var count: Int = 0

        override fun enough(data: String): Boolean {
            println(mapper.readValue(data, object: TypeReference<ContainerStats>() {}))
            count++

            return count == 5
        }
    }
    fun startContainer(container: ContainerModel) = add(post(endpoint = "containers/${container.id}/start", options = "", contenttype = null))
    fun statContainer(container: ContainerModel) {
        add(get(endpoint = "containers/${container.id}/stats", options = ""), StatsCallback())
    }
    fun createContainer(container: ContainerModel): String {
        ContainerManager.log("Creating container ${container.props.name}")
        val map = HashMap<String, Any>()
        val exposedPorts = HashMap<String, Any>()
        val portBindings = HashMap<String, Any>()
        container.props.ports.forEach {
            val identifier = it.split(":").first()
            val port = it.split(":").last()
            exposedPorts[identifier] = HashMap<String, String>()
            portBindings[identifier] = ArrayList<HashMap<String, String>>()
            (portBindings[identifier] as ArrayList<HashMap<String, String>>).add(HashMap(mapOf(Pair("HostPort", port))))
        }
        map["Hostname"]=""
        map["Domainname"]=""
        map["User"]=""
        map["AttachStdin"]=false
        map["AttachStdout"]=true
        map["AttachStderr"]=true
        map["Tty"]=false
        map["OpenStdin"]=false
        map["StdinOnce"]=false
        map["HostConfig"] = HashMap(mapOf(Pair("PortBindings", portBindings)))
        map["Image"] = container.imageId?:throw RuntimeException("imageId is missing")
        map["ExposedPorts"] = exposedPorts
        map["HostConfig"] = HashMap(mapOf(Pair("PortBindings", portBindings)))


        val obj = JSONObject(add(
            post(data = mapper.writeValueAsBytes(map), endpoint = "containers/create", options = ""),
            PrintingCallback()
        ))
        return obj.getString("Id")
    }
}

class PrintingCallback: DataCallback() {

    override fun enough(data: String): Boolean {
        println(data)
        return false
    }
}


abstract class DataCallback {
    abstract fun enough(data: String): Boolean
}
data class ParseData(var chunkData: StringBuilder = StringBuilder(), var masked: Boolean = true, var chunkLength: Int? = null, var chunked: Boolean = false, var contentPart: Boolean = false, var lastval: Int = 0, var contentLength: Int? = null, var headerPart: Boolean = false, var count: Int = 0, var position: Int = 0)

interface Handler {
    fun handle(data: String): Boolean
}

data class CPUUsage(val percpu_usage: Array<Long>?, val usage_in_usermode: Long?, val total_usage: Long?, val usage_in_kernelmode: Long?)
data class CPUStats (val cpu_usage: CPUUsage, val system_cpu_usage: Long, val cpu: Long, val online_cpus: Int,)
data class ContainerStats (val cpu_stats: CPUStats, val cpu_system_usage: Long)
