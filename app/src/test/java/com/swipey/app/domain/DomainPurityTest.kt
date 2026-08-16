package com.swipey.app.domain

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class DomainPurityTest {
    @Test fun noDomainFileImportsAndroid() {
        val domainDir = File("src/main/java/com/swipey/app/domain")
        val sources = domainDir.walkTopDown().filter { it.extension == "kt" }.toList()
        assert(sources.isNotEmpty()) { "no domain sources found at ${domainDir.absolutePath}" }
        val offenders = sources.filter { file ->
            file.readLines().any { it.trimStart().startsWith("import android") }
        }
        assertEquals("domain must stay Android-free", emptyList<String>(), offenders.map { it.name })
    }
}
