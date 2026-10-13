package com.forma.health

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class UpdatePolicyTest {
    private val apk="https://github.com/srinidhibhat45/forma/releases/download/build-4/forma.apk"
    private val page="https://github.com/srinidhibhat45/forma/releases/tag/build-4"
    private fun metadata()=JSONObject().apply {
        put("schemaVersion",1);put("packageName","com.forma.health")
        put("versionCode",4);put("versionName","1.2.0")
        put("sha256","ab".repeat(32));put("apkBytes",1024)
    }
    private fun parse(json:JSONObject)=UpdatePolicy.manifest(json.toString(),apk,1024,page,"Changes")
    private fun rejected(json:JSONObject) { assertThrows(Exception::class.java) { parse(json) } }
    @Test fun trustedReleaseSurvivesCacheRoundTrip() {
        val release=parse(metadata())
        assertEquals(release,UpdatePolicy.stored(UpdatePolicy.encode(release)))
        assertTrue(UpdatePolicy.newer(release.versionCode,3))
    }
    @Test fun rejectsCoercedFractionalAndOutOfRangeVersions() {
        listOf<Any>("4",4.5,0,-1,2_100_000_001L).forEach { rejected(metadata().put("versionCode",it)) }
    }
    @Test fun refusesOtherPackagesCorruptHashesAndMismatchedSizes() {
        rejected(metadata().put("packageName","other.app"))
        rejected(metadata().put("sha256","ab".repeat(31)))
        rejected(metadata().put("sha256","xx".repeat(32)))
        rejected(metadata().put("apkBytes",1025))
        rejected(metadata().put("apkBytes",UpdatePolicy.MAX_APK_BYTES+1))
        rejected(metadata().put("schemaVersion",2))
    }
    @Test fun refusesDowngradesAndEqualVersions() {
        assertFalse(UpdatePolicy.newer(3,3));assertFalse(UpdatePolicy.newer(2,3))
        assertFalse(UpdatePolicy.newer(2_100_000_001,3))
    }
    @Test fun confinesAssetsToThisRepositoryAndHttps() {
        assertTrue(UpdatePolicy.releaseAssetUrl(apk))
        listOf(apk.replace("https:","http:"),apk.replace("srinidhibhat45","someone"),
            apk.replace("github.com","github.com.evil.example"),apk.replace("github.com","user@github.com"),
            apk.replace("github.com","github.com:8080"),apk.replace("build-4","%2e%2e"),"$apk#fragment"
        ).forEach { assertFalse(it,UpdatePolicy.releaseAssetUrl(it)) }
        assertFalse(UpdatePolicy.downloadHost("https://example.com/forma.apk"))
        assertTrue(UpdatePolicy.downloadHost("https://release-assets.githubusercontent.com/file"))
    }
}
