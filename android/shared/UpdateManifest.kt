package com.forma.health

import java.net.URI
import org.json.JSONObject

/** Public distribution metadata only. No health records or account credentials enter this model. */
data class AppRelease(
    val versionCode:Int,
    val versionName:String,
    val apkUrl:String,
    val sha256:String,
    val bytes:Long,
    val notes:String,
    val pageUrl:String
)

object UpdatePolicy {
    const val REPOSITORY = "srinidhibhat45/forma"
    const val API_URL = "https://api.github.com/repos/$REPOSITORY/releases/latest"
    const val MAX_APK_BYTES = 100L * 1024 * 1024
    const val FOREGROUND_INTERVAL_MS = 15L * 60 * 1000

    fun releaseAssetUrl(value:String):Boolean = runCatching {
        val uri=URI(value)
        uri.scheme=="https" && uri.host=="github.com" && uri.userInfo==null &&
            uri.port in listOf(-1,443) && uri.path.startsWith("/$REPOSITORY/releases/download/") &&
            uri.fragment==null && uri.path.split('/').none { it==".." || it=="." }
    }.getOrDefault(false)

    fun downloadHost(value:String):Boolean = runCatching {
        val uri=URI(value)
        uri.scheme=="https" && uri.userInfo==null && uri.port in listOf(-1,443) &&
            uri.host in setOf("api.github.com","github.com","release-assets.githubusercontent.com","objects.githubusercontent.com","github-releases.githubusercontent.com")
    }.getOrDefault(false)

    fun newer(candidate:Int,installed:Int) = candidate>installed && candidate<=2_100_000_000

    fun manifest(json:String,apkUrl:String,assetBytes:Long,releasePage:String,notes:String):AppRelease {
        require(releaseAssetUrl(apkUrl)) { "The update download is outside Forma’s repository." }
        val root=JSONObject(json)
        require(root.getInt("schemaVersion")==1 && root.getString("packageName")=="com.forma.health") { "This release is not a Forma update." }
        // Reject coercion of strings/fractional numbers into an Android version code.
        val raw=root.get("versionCode")
        require(raw is Number && raw.toDouble()==raw.toLong().toDouble() && raw.toLong() in 1..2_100_000_000L) { "Invalid update version." }
        val version=raw.toInt()
        val name=root.getString("versionName")
        val sha=root.getString("sha256").lowercase()
        val size=root.getLong("apkBytes")
        require(name.length in 1..80 && Regex("^[a-f0-9]{64}$").matches(sha)) { "Invalid update metadata." }
        require(size in 1..MAX_APK_BYTES && size==assetBytes) { "The update file size does not match its release." }
        val page=URI(releasePage)
        require(page.scheme=="https" && page.host=="github.com" && page.userInfo==null && page.path.startsWith("/$REPOSITORY/releases/tag/"))
        return AppRelease(version,name,apkUrl,sha,size,notes.take(4000),releasePage)
    }

    fun encode(release:AppRelease):String = JSONObject().apply {
        put("schemaVersion",1);put("packageName","com.forma.health")
        put("versionCode",release.versionCode);put("versionName",release.versionName)
        put("sha256",release.sha256);put("apkBytes",release.bytes)
        put("apkUrl",release.apkUrl);put("notes",release.notes);put("pageUrl",release.pageUrl)
    }.toString()

    fun stored(json:String):AppRelease {
        val root=JSONObject(json)
        return manifest(json,root.getString("apkUrl"),root.getLong("apkBytes"),root.getString("pageUrl"),root.optString("notes"))
    }
}
