package com.anireku.app.source.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedParserTest {

    private val valid = """
        {
          "name": "My library",
          "entries": [
            {
              "anilistId": 16498,
              "episodes": [
                {
                  "number": 1,
                  "title": "To You, in 2000 Years",
                  "streams": [
                    { "url": "https://cdn.example.test/ep1/master.m3u8", "quality": "1080p" }
                  ]
                }
              ]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `parses a valid feed`() {
        val feed = FeedParser.parse(valid).getOrThrow()
        assertEquals("My library", feed.name)
        assertEquals(1, feed.entries.size)
        assertEquals(16498L, feed.entries[0].anilistId)
        assertEquals("1080p", feed.entries[0].episodes[0].streams[0].quality)
    }

    @Test
    fun `rejects an empty catalogue`() {
        val result = FeedParser.parse("""{ "entries": [] }""")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("no entries"))
    }

    @Test
    fun `rejects non http stream urls`() {
        val bad = """
            { "entries": [ { "anilistId": 1, "episodes": [
                { "number": 1, "streams": [ { "url": "file:///sdcard/x.m3u8" } ] } ] } ] }
        """.trimIndent()
        val result = FeedParser.parse(bad)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("http"))
    }

    @Test
    fun `rejects episode number zero`() {
        val bad = """
            { "entries": [ { "anilistId": 1, "episodes": [
                { "number": 0, "streams": [] } ] } ] }
        """.trimIndent()
        assertTrue(FeedParser.parse(bad).isFailure)
    }

    @Test
    fun `maps a feed episode to a domain episode`() {
        val feed = FeedParser.parse(valid).getOrThrow()
        val ep = FeedParser.toEpisode("16498", "userfeed", feed.entries[0].episodes[0])
        assertEquals(1, ep.number)
        assertEquals("To You, in 2000 Years", ep.title)
        assertEquals(listOf("1080p"), ep.availableQualities)
    }

    @Test
    fun `keeps the quality label on the stream link`() {
        val feed = FeedParser.parse(valid).getOrThrow()
        val links = FeedParser.toStreamLinks(feed.entries[0].episodes[0])
        assertEquals(1, links.size)
        assertTrue(links[0].isHls)
    }
}
