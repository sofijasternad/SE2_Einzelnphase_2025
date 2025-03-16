package at.aau.serg

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ValidationTests {
    @Test
    fun `successful validation with abgabe xml`() = runBlocking {
        val exitCode = ValidationRunner.runValidation(arrayOf("abgabe.xml"))
        assertEquals(ExitCode.Success, exitCode)
    }
}
