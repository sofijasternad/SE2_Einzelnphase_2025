package at.aau.serg

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import org.xml.sax.SAXException
import java.io.File
import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory

@Serializable
@XmlSerialName("person", "", "")
data class Person(
    @XmlElement(true)
    val name: String,

    @XmlElement(true)
    val matrikelnummer: String,

    @XmlElement(true)
    val lastcommithash: String,

    @XmlElement(true)
    val githubusername: String,

    @XmlElement(true)
    val repositoryname: String,

    @XmlElement(true)
    val repositoryurl: String
)

object PersonParser {
    private val xmlSerializer = XML {
        autoPolymorphic = false
    }

    fun parse(xmlFile: File): Person {
        return xmlSerializer.decodeFromString(
            Person.serializer(),
            xmlFile.readText()
        )
    }
}

sealed class ExitCode(val code: Int, val message: String) {
    data object Success : ExitCode(
        0,
        """✅ XML File Validation Successful""".trimMargin()
    )

    data object MissingInputPath : ExitCode(
        1,
        """❌ Argument Error
           |No input path specified
           |
           |Troubleshooting:
           |- Provide path to XML file as argument""".trimMargin()
    )

    data object InputFileNotFound : ExitCode(
        2,
        """❌ File Access Error
           |Specified input file not found
           |
           |Troubleshooting:
           |- Verify exact file path spelling
           |- Check file exists at location
           |- Validate read permissions
           |- Ensure path contains no special characters""".trimMargin()
    )

    data object SchemaNotFound : ExitCode(
        2,
        """❌ Schema Configuration Error
           |Required validation schema is missing
           |
           |Troubleshooting:
           |- Verify schema file in resources/schema/
           |- Check schema file name matches 'person.xsd'""".trimMargin()
    )

    data object SchemaValidationError : ExitCode(
        3,
        """❌ XML Structure Error
           |Document structure violates schema requirements
           |
           |Troubleshooting:
           |- Check all required fields are present
           |- Verify element order matches schema
           |- Ensure text content matches expected types
           |- Validate element nesting hierarchy""".trimMargin()
    )

    data object RepositoryValidationFailed : ExitCode(
        4,
        """❌ Repository Access Error
           |GitHub repository validation unsuccessful
           |
           |Troubleshooting:
           |- Verify repository URL is correct
           |- Check repository visibility (must be public)
           |- Ensure network connectivity
           |- Validate URL formatting""".trimMargin()
    )

    data object CommitValidationFailed : ExitCode(
        5,
        """❌ Commit Verification Error
           |Specified commit not found in repository
           |
           |Troubleshooting:
           |- Verify 40-character SHA-1 hash format
           |- Check commit exists in remote history
           |- Validate repository permissions""".trimMargin()
    )

    data object UnexpectedError : ExitCode(
        6,
        """🔥 Unexpected Application Error
           |An unexpected failure occurred during validation
           |
           |Troubleshooting:
           |- Check network connection stability
           |- Verify input file integrity
           |- Retry validation process""".trimMargin()
    )
}

class ValidationException(val exitCode: ExitCode, cause: Throwable? = null) :
    Exception("${exitCode.message}${cause?.message?.let { "\n\n$it" } ?: ""}", cause)

class XsdValidator(private val xsdPath: String) {
    fun validate(xmlFile: File) {
        val schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
        val xsdStream = javaClass.getResourceAsStream(xsdPath)
            ?: throw ValidationException(ExitCode.SchemaNotFound)

        xsdStream.use { stream ->
            val schema = schemaFactory.newSchema(StreamSource(stream))
            val validator = schema.newValidator()
            try {
                validator.validate(StreamSource(xmlFile))
            } catch (e: SAXException) {
                throw ValidationException(ExitCode.SchemaValidationError, e)
            }
        }
    }
}

class GitHubClientFactory {
    fun create(timeoutMillis: Long = 10000): HttpClient {
        return HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = timeoutMillis
            }
        }
    }
}

class RepositoryValidator(private val client: HttpClient) {
    suspend fun validate(repoUrl: String) {
        val response = client.get(repoUrl)
        if (response.status != HttpStatusCode.OK) {
            throw ValidationException(
                ExitCode.RepositoryValidationFailed,
                Exception("Repository inaccessible or private")
            )
        }
    }
}

class CommitValidator(private val client: HttpClient) {
    suspend fun validate(repoUrl: String, commitHash: String) {
        val apiUrl = "${repoUrl.replace("github.com", "api.github.com/repos")}/commits/$commitHash"
        val response = client.get(apiUrl) {
            headers { append("Accept", "application/vnd.github.v3+json") }
        }

        if (response.status != HttpStatusCode.OK) {
            throw ValidationException(
                ExitCode.CommitValidationFailed,
                Exception("Commit not found in repository")
            )
        }
    }
}

object ValidationRunner {
    suspend fun runValidation(args: Array<String>): ExitCode {
        return try {
            if (args.isEmpty()) {
                throw ValidationException(ExitCode.MissingInputPath)
            }

            val xmlFile = File(args[0])
            if (!xmlFile.exists() || !xmlFile.isFile) {
                throw ValidationException(ExitCode.InputFileNotFound)
            }

            XsdValidator("/schema/person.xsd").validate(xmlFile)
            val person = PersonParser.parse(xmlFile)

            GitHubClientFactory().create().use { httpClient ->
                RepositoryValidator(httpClient).validate(person.repositoryurl)
                CommitValidator(httpClient).validate(person.repositoryurl, person.lastcommithash)
            }

            println(ExitCode.Success.message)
            ExitCode.Success
        } catch (e: ValidationException) {
            System.err.println(e.message ?: e.exitCode.message)
            e.exitCode
        } catch (e: Exception) {
            System.err.println("${ExitCode.UnexpectedError.message}: ${e.message ?: "Unknown error"}")
            ExitCode.UnexpectedError
        }
    }
}

suspend fun main(args: Array<String>) {
    val exitCode = ValidationRunner.runValidation(args)
    kotlin.system.exitProcess(exitCode.code)
}
