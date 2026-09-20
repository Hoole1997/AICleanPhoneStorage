package io.docview.push.config

import java.io.File
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class DayContentPoolTest {
    private fun pools() = DayContentPool.days.associateWith { day ->
        parseDayContentPool(File("src/main/assets/${DayContentPool.key(day)}.json").readText(), day)
    }
    private fun json(day: Int, id: String = "item", translations: String = "{}") = """
        {"schemaVersion":2,"pool":"D$day","defaultLanguage":"en","contents":[
        {"id":"$id","title":"English","desc":"Description","buttonText":"Open","iconType":1,"actionType":1,"translations":$translations}]}
    """.trimIndent()

    @Test fun all141SuppliedItemsSurviveParsingWithTheirRealRoutesAndTranslations() {
        val values = pools()
        assertEquals(listOf(29, 28, 28, 28, 28), values.values.map { it.contents.size })
        val combined = DayContentCatalog(values).contents(6)
        assertEquals(141, combined.size)
        assertEquals(141, combined.map { it.id }.toSet().size)
        assertEquals(values.values.flatMap { it.contents }.map { it.id }, combined.map { it.id })
        assertEquals(setOf(1, 2, 3, 4, 5, 1001, 1002, 1003, 1004, 1005, 1006, 1007), combined.map { it.actionType }.toSet())
        for (item in combined) {
            assertEquals(item.destination, item.iconDestination)
            assertEquals(setOf("pt-BR", "es-MX", "es-ES", "id-ID", "hi-IN", "ja-JP", "ko-KR"), item.translations.keys)
            for (language in item.translations.keys) {
                val localized = item.localized(language)
                assertEquals(item.id, localized.id)
                assertEquals(item.actionType, localized.actionType)
                assertEquals(item.translations.getValue(language).title, localized.title)
                assertEquals(item.translations.getValue(language).desc, localized.desc)
                assertEquals(item.translations.getValue(language).buttonText, localized.buttonText)
                assertTrue(localized.translations.isEmpty())
            }
        }
    }

    @Test fun naturalDaysChangeAtMidnightAndD6ContinuesAcrossLaterDaysAndRestarts() {
        val first = LocalDate.of(2026, 3, 7).toEpochDay() // 跨夏令时仍按自然日，而非 24 小时。
        val cursors = mutableMapOf<Int, Int>()
        fun rotation() = DayContentRotation(first, { cursors[it] ?: 0 }, { day, index -> cursors[day] = index })
        val engine = rotation()
        val catalog = DayContentCatalog(pools())
        assertEquals(1, engine.poolDay(first - 100))
        for (offset in 0L..4L) {
            assertEquals((offset + 1).toInt(), engine.poolDay(first + offset))
            assertEquals(catalog.contents((offset + 1).toInt()).first().id,
                engine.next(catalog, first + offset, "en").id)
        }
        assertEquals(catalog.contents(6)[0].id, engine.next(catalog, first + 5, "en").id)
        assertEquals(catalog.contents(6)[1].id, rotation().next(catalog, first + 6, "ja").id)
        assertEquals(6, engine.poolDay(first + 300))
    }

    @Test fun dayPoolWrapsAndLanguageSwitchDoesNotRestartOrDuplicateEntries() {
        val values = pools()
        val catalog = DayContentCatalog(values)
        val cursors = mutableMapOf<Int, Int>()
        val engine = DayContentRotation(100, { cursors[it] ?: 0 }, { day, index -> cursors[day] = index })
        val first = engine.next(catalog, 100, "ja")
        assertEquals(values.getValue(1).contents[0].translations.getValue("ja-JP").title, first.title)
        val second = engine.next(catalog, 100, "pt-BR")
        assertEquals(values.getValue(1).contents[1].id, second.id)
        repeat(27) { engine.next(catalog, 100, "en") }
        assertEquals(first.id, engine.next(catalog, 100, "en").id)
        repeat(141) { engine.next(catalog, 105, "en") }
        assertEquals(first.id, engine.next(catalog, 105, "en").id)
    }

    @Test fun combinedPoolIsNotTruncatedToTheSinglePoolLimit() {
        val values = DayContentPool.days.associateWith { day ->
            DayContentPool(day, (1..70).map { index -> Content("d${day}_$index", "Title", "Text", "Open", 1, 1) })
        }
        val catalog = DayContentCatalog(values)
        assertEquals(350, catalog.contents(6).size)
        assertEquals("d5_70", catalog.contents(6).last().id)
    }

    @Test fun localeMatchesExactRegionThenLanguageAndKnownRegionalDefault() {
        val item = pools().getValue(1).contents[1]
        for ((requested, expected) in mapOf("es-ES" to "es-ES", "es-MX" to "es-MX", "es" to "es-MX",
            "es-AR" to "es-MX", "pt" to "pt-BR", "id" to "id-ID", "hi" to "hi-IN", "ja" to "ja-JP", "ko" to "ko-KR")) {
            assertEquals(item.translations.getValue(expected).title, item.localized(requested).title)
        }
        assertEquals(item.title, item.localized("fr").title)
        assertEquals(item.title, item.localized("zh-Hans").title)
        assertEquals(item.title, item.localized("en-US").title)
    }

    @Test fun incompleteTranslationFallsBackAsOneMessageAndNeverChangesAction() {
        val item = parseDayContentPool(json(1, translations = """{"ja-JP":{"title":"日本語"},"fr":{"title":"Titre","desc":"Texte","buttonText":"Ouvrir","actionType":1006}}"""), 1).contents.single()
        assertEquals("English", item.localized("ja").title)
        assertEquals("Description", item.localized("ja").desc)
        assertEquals("Open", item.localized("ja").buttonText)
        assertEquals("Titre", item.localized("fr").title)
        assertEquals(1, item.localized("fr").actionType)
    }

    @Test fun eachRemoteKeyFallsBackIndependentlyAndOnlyValidRemoteIsCached() {
        var cachedWrite: String? = null
        fun resolve(remote: String, cached: String?, current: DayContentPool? = null) = resolveDayContentPool(2,
            remote, current, { cached }, { json(2, "local") }, { cachedWrite = it })
        assertEquals("remote", resolve(json(2, "remote"), json(2, "cached")).contents.single().id)
        assertNotNull(cachedWrite)
        cachedWrite = null
        assertEquals("cached", resolve("broken", json(2, "cached")).contents.single().id)
        assertEquals("cached", resolve(json(1, "wrong_day"), json(2, "cached")).contents.single().id)
        assertEquals("local", resolve("", "broken").contents.single().id)
        assertEquals("current", resolve("", json(2, "cached"), parseDayContentPool(json(2, "current"), 2)).contents.single().id)
        assertNull(cachedWrite)
    }

    @Test fun invalidOversizedAndDuplicatePoolsCannotReplaceAValidPool() {
        for (bad in listOf("[]", "{}", json(2), json(1).replace("\"schemaVersion\":2", "\"schemaVersion\":3"),
            json(1).replace("\"defaultLanguage\":\"en\"", "\"defaultLanguage\":\"ja\""),
            " ".repeat(MAX_DAY_POOL_CHARS + 1))) {
            assertTrue(runCatching { parseDayContentPool(bad, 1) }.isFailure)
        }
        val content = """{"id":"same","title":"Title","desc":"Description","buttonText":"Open","actionType":1,"iconType":1}"""
        val duplicate = """{"schemaVersion":2,"pool":"D1","defaultLanguage":"en","contents":[$content,$content]}"""
        assertTrue(runCatching { parseDayContentPool(duplicate, 1) }.isFailure)
    }
}
