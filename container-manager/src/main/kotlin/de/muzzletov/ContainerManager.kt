package de.muzzletov

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

import de.muzzletov.model.*

import java.io.File
import java.nio.file.Path
import java.util.zip.CRC32
import java.util.zip.Checksum

object ContainerManager {
    private val mapper = jacksonObjectMapper()
    private val repository = HashMap<String, ContainerModel>()
    private var rebuildJSON: Boolean = false
    private fun loadContainerData(): HashMap<String, ContainerModel> {
        val data = getContainerJSON().readText()
        val containers = HashMap<String, ContainerModel>()
        val props: List<ContainerProps> = mapper.readValue(data, object: TypeReference<List<ContainerProps>>() {})
            props.forEach {
                containers[it.name] = ContainerModel(
                    it,
                    State.created
                )
            }
        return containers
    }

    private fun getContainerJSON() = javaClass.classLoader.getResource("containers.json")
        ?:throw IllegalArgumentException("containers.json doesnt exist")

    private fun getPath(relPath: String): String = "${Path.of("..").toAbsolutePath()}${File.separatorChar}${relPath}"

    @JvmStatic
    fun writeJSON(data: String) {
        val generatedPath = getContainerJSON().path
        val srcPath = "${generatedPath.substring(0, generatedPath.indexOf("build"))}src/test/resources/containers.json"
        File(generatedPath).writeText(data)
        File(srcPath).writeText(data)
    }

    @JvmStatic
    fun log(message: String) = println("[Container-Manager] \u001B[33m$message\u001B[0m")

    init {
        val data = loadContainerData()
        val images = DocketClient.getAllImages()
        val containers = DocketClient.getAllContainers()

        data.forEach {
            val tag = tag(it.key)
            val image = images
                .find {
                    image -> image.RepoTags
                    ?.find {
                            tags -> tags.contains(tag)
                    } != null
                }
            val id =
                if(rebuildImage(it.value.props) || image?.Id  == null) {
                    DocketClient.buildImage(ImageOut(path = getPath(it.value.props.file), tags = arrayListOf(tag)))
                } else
                    image.Id

            val container: ContainerModel = it.value
            container.imageId = id
            container.imageName = tag
            container.id =
                containers
                .filter {
                        c->
                            c.ImageID == id }
                .find {
                    c->matchesBindings(c, it.value.props.ports)
                        true
                }
                ?.Id?:
                DocketClient.createContainer(container)
            container.state =
                State.valueOf(
                    (containers.filter {
                            c-> c.ImageID == id
                        }.find {
                            c->matchesBindings(c, it.value.props.ports)
                            true
                        }
                        ?.State)?:"created"
                )

            repository[it.key] = container
        }

        if(rebuildJSON) {
            log("Writing 'containers.json'")
            mapper.configure(SerializationFeature.INDENT_OUTPUT, true)
            writeJSON(mapper.writeValueAsString(repository.map { it.value.props }))
        }
    }

    private fun matchesBindings(container: Container, ports: ArrayList<String>):
            Boolean = ports.containsAll(container.Ports.map { "${it.PrivatePort}/${it.Type}:${it.PublicPort}" })

    private fun rebuildImage(container: ContainerProps): Boolean {
        val path = getPath(container.file)
        val bytes: ByteArray = File(path).readBytes()
        val crc32: Checksum = CRC32()
        val checksum: Long? = container.checksum?.toLong()

        crc32.update(bytes, 0, bytes.size)

        if(checksum != crc32.value) {
            container.checksum = crc32.value.toString()
            log("Writing checksum")
            rebuildJSON = true
        }

        return checksum != crc32.value
    }

    fun contains(name: String) = repository.contains(name)

    @JvmStatic
    fun waitFor(name: String) {
        if(!contains(name))
            throw RuntimeException("Container does not exist: '$name' in 'containers.json'")
        if(repository[name]?.state == State.running) {
            log("Container ${repository[name]!!.props.name} already running")
            return
        }
        DocketClient.startContainer(repository[name]!!)
        log("Started Container ${repository[name]!!.props.name}")
    }

    private fun tag(name: String) = "${name}-image/container-manager"
}
