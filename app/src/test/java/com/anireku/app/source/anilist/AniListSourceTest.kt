package com.anireku.app.source.anilist

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class AniListSourceTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    /** Short timeouts: a wrong expectation must fail in seconds, not hang the suite. */
    private fun source() = AniListSource(
        http = OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .build(),
        endpoint = server.url("/").toString(),
    )

    private val pageBody = """
        {
          "data": {
            "Page": {
              "pageInfo": { "currentPage": 1, "hasNextPage": true },
              "media": [
                {
                  "id": 16498,
                  "title": { "romaji": "Shingeki no Kyojin", "english": "Attack on Titan" },
                  "coverImage": { "extraLarge": "https://img.test/aot.jpg" },
                  "episodes": 25,
                  "status": "FINISHED",
                  "format": "TV",
                  "averageScore": 84,
                  "seasonYear": 2013,
                  "isAdult": false,
                  "genres": ["Action"]
                }
              ]
            }
          }
        }
    """.trimIndent()

    private val detailBody = """
        { "data": { "Media": {
            "id": 1,
            "title": { "romaji": "Test" },
            "description": "<b>Bold</b> text",
            "genres": ["Drama"],
            "bannerImage": null,
            "studios": { "edges": [ { "node": { "name": "MAPPA" } } ] }
        } } }
    """.trimIndent()

    @Test
    fun `trending maps the GraphQL payload to summaries`() = runBlocking {
        server.enqueue(MockResponse().setBody(pageBody))

        val page = source().trending()

        assertEquals(1, page.items.size)
        assertEquals("Shingeki no Kyojin", page.items[0].title)
        assertEquals(8.4, page.items[0].score!!, 0.001)
        assertEquals(2013, page.items[0].year)
        assertTrue(page.hasNextPage)

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        val sent = recorded.body.readUtf8()
        assertTrue(sent.contains("TRENDING_DESC"))
        assertTrue("request must carry a variables object", sent.contains("\"variables\""))
    }

    @Test
    fun `rate limit surfaces as a readable error`() = runBlocking {
        // One 429: what matters is the message the caller gets, not how many sockets the retry opened.
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setBody("""{"errors":[{"message":"Too Many Requests"}]}"""),
        )

        val error = runCatching { source().search("one piece") }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(
            "unexpected failure: $error",
            error!!.message!!.contains("rate limit", ignoreCase = true),
        )
    }

    @Test
    fun `detail strips html from the synopsis`() = runBlocking {
        server.enqueue(MockResponse().setBody(detailBody))

        val detail = source().detail("1")

        assertEquals("Bold text", detail.synopsis)
        assertEquals(listOf("MAPPA"), detail.studios)
    }

    @Test
    fun `a 404 is reported as missing, not as a network error`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))

        val error = runCatching { source().detail("999999999") }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error!!.message!!.contains("no record"))
    }

    @Test
    fun `anilist never fabricates stream links`() = runBlocking {
        val episode = com.anireku.app.core.model.Episode(
            animeId = "1",
            sourceId = AniListSource.SOURCE_ID,
            number = 1,
            title = "Episode 1",
        )
        assertTrue(source().streams(episode).isEmpty())
    }
}
