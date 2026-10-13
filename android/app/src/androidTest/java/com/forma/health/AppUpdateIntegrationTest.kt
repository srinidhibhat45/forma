package com.forma.health

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in integration check against the public release, with no health data in requests. */
class AppUpdateIntegrationTest {
    @Test fun publishedUpdateDownloadsAndPassesIdentityChecks()=runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveUpdateTest")=="true")
        val client=GitHubUpdates(InstrumentationRegistry.getInstrumentation().targetContext)
        val release=requireNotNull(client.latest())
        assertTrue(UpdatePolicy.newer(release.versionCode,BuildConfig.VERSION_CODE))
        val file=client.download(release) {}
        assertEquals(release.bytes,file.length())
        client.verified(file,release)
    }
}
