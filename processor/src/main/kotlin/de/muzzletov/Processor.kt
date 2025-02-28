package de.muzzletov

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import java.io.OutputStream
import java.util.*
import kotlin.collections.HashMap

class Processor(private val codeGenerator: CodeGenerator) :
    SymbolProcessor {
    private val annotations = HashSet<String>()
    private var processed = false
    private lateinit var file: OutputStream

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (processed) return emptyList()

        val symbols: Sequence<KSAnnotation>? = NeedsContainer::class.qualifiedName?.let {
            resolver
                .getSymbolsWithAnnotation(it).flatMap { e -> e.annotations }
        }

        symbols?.forEach {
            val fields = extractData(it) ?: return@forEach
            if (!annotations.contains(fields["name"]))
                createFile(fields)
        }

        processed = true
        return emptyList()
    }

    private fun extractData(annotation: KSAnnotation): HashMap<String, String>? {
        val fields = HashMap<String, String>()
        fields["name"] =
            annotation.arguments.find { it.name?.getShortName() == "name" }?.value?.toString() ?: return null

        return fields
    }

    private fun createFile(fields: HashMap<String, String>) {
        val packageName = "de.muzzletov"
        val className = "${fields["name"]?.split(",")?.joinToString("") { it.trim() }}Deployment"

        file = codeGenerator.createNewFile(
            dependencies = Dependencies(false),
            packageName,
            fileName = className
        )

        file.write(
            """
                |package $packageName
                |
                |import de.muzzletov.ContainerManager
                |
                |import org.junit.jupiter.api.extension.AfterAllCallback
                |import org.junit.jupiter.api.extension.BeforeAllCallback
                |import org.junit.jupiter.api.extension.ExtensionContext
                |
                |object $className:BeforeAllCallback, AfterAllCallback {
                |   val containers = arrayOf(${fields["name"]?.split(",")?.joinToString(", ", transform = { """"$it"""" })})
                |   @JvmStatic
                |   fun waitFor() {
                |       containers.forEach {
                |           container->
                |               ContainerManager.waitFor(container.trim())
                |       }
                |   }
                |   @Throws(Exception::class)
                |   override fun afterAll(context: ExtensionContext?): Unit {
                |
                |   }
                |   @Throws(Exception::class)
                |   override fun beforeAll(context: ExtensionContext?): Unit {
                |       waitFor()
                |   }
                |}
                """.trimMargin().toByteArray()
        )
        file.close()
        annotations.add(fields["name"]!!)
    }
}
