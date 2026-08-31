package com.nousresearch.hermes.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class LocalizationResourcesTest {
    @Test
    fun simplifiedChineseCoversEveryTranslatableResource() {
        val english = resources("src/main/res/values/strings.xml")
        val chinese = resources("src/main/res/values-zh-rCN/strings.xml")
        val expected = english.filterValues { it.translatable }.keys
        assertEquals(expected, chinese.keys)
    }

    @Test
    fun formatArgumentsMatchAcrossLocales() {
        val english = resources("src/main/res/values/strings.xml")
        val chinese = resources("src/main/res/values-zh-rCN/strings.xml")
        english.filterValues { it.translatable }.forEach { (name, resource) ->
            assertEquals("Format arguments differ for $name", placeholders(resource.text), placeholders(chinese.getValue(name).text))
        }
    }

    @Test
    fun localeConfigDeclaresEnglishAsTheUnqualifiedLocale() {
        assertTrue(File("src/main/res/resources.properties").readText().contains("unqualifiedResLocale=en-US"))
    }

    private fun resources(path: String): Map<String, Resource> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path))
        return (0 until document.documentElement.childNodes.length)
            .mapNotNull { document.documentElement.childNodes.item(it) as? Element }
            .associate { element ->
                element.getAttribute("name") to Resource(
                    text = element.textContent,
                    translatable = element.getAttribute("translatable") != "false",
                )
            }
    }

    private fun placeholders(value: String): Set<String> =
        Regex("%(?:\\d+\\$)?[dfeEgGsS]").findAll(value).map { it.value.lowercase() }.toSet()

    private data class Resource(val text: String, val translatable: Boolean)
}
