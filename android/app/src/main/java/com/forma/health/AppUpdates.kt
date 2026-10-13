package com.forma.health

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class UpdateStore(context:Context) {
    val preferences:SharedPreferences=context.getSharedPreferences("forma_updates",Context.MODE_PRIVATE)
    fun automatic()=preferences.getBoolean("automatic",true)
    fun setAutomatic(value:Boolean) { preferences.edit().putBoolean("automatic",value).apply() }
    fun hasPublished()=preferences.getBoolean("published",false)
    fun lastChecked()=preferences.getLong("checked",0)
    fun lastAttempt()=preferences.getLong("attempt",0)
    fun attempted() { preferences.edit().putLong("attempt",System.currentTimeMillis()).apply() }
    fun release():AppRelease? = preferences.getString("release",null)?.let { runCatching { UpdatePolicy.stored(it) }.getOrNull() }?.takeIf { UpdatePolicy.newer(it.versionCode,BuildConfig.VERSION_CODE) }
    @Synchronized fun checked(release:AppRelease?) {
        val existing=this.release()
        val keep=release?.takeIf { UpdatePolicy.newer(it.versionCode,BuildConfig.VERSION_CODE) }
        val selected=if(existing!=null && (keep==null || existing.versionCode>keep.versionCode))existing else keep
        preferences.edit().putLong("checked",System.currentTimeMillis()).putBoolean("published",release!=null || existing!=null).apply {
            if(selected==null)remove("release") else putString("release",UpdatePolicy.encode(selected))
        }.apply()
    }
}

/** GET-only HTTPS client; redirects are restricted to GitHub's release infrastructure. */
class GitHubUpdates(private val context:Context) {
    private fun connection(url:String):HttpURLConnection {
        var target=url
        repeat(6) {
            require(UpdatePolicy.downloadHost(target)) { "Unexpected update download host." }
            val connection=URI(target).toURL().openConnection() as HttpURLConnection
            connection.instanceFollowRedirects=false
            connection.connectTimeout=15_000;connection.readTimeout=30_000
            connection.setRequestProperty("User-Agent","Forma-Android/${BuildConfig.VERSION_NAME}")
            connection.setRequestProperty("Accept",if(URI(target).host=="api.github.com")"application/vnd.github+json" else "application/octet-stream")
            if(URI(target).host=="api.github.com")connection.setRequestProperty("X-GitHub-Api-Version","2022-11-28")
            val status=connection.responseCode
            if(status in listOf(301,302,303,307,308)) {
                val next=connection.getHeaderField("Location") ?: run { connection.disconnect();throw IOException("Incomplete update redirect.") }
                val resolved=URI(target).resolve(next).toString();connection.disconnect();target=resolved
            } else return connection
        }
        throw IOException("Too many update redirects.")
    }
    private suspend fun text(url:String,limit:Int,allowMissing:Boolean=false):String? {
        val connection=connection(url)
        try {
            if(connection.responseCode==404 && allowMissing)return null
            requireSuccess(connection.responseCode)
            return connection.inputStream.use { input ->
                val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                while(true) {
                    currentCoroutineContext().ensureActive()
                    val n=input.read(buffer);if(n<0)break
                    require(out.size()+n<=limit) { "Update metadata is too large." };out.write(buffer,0,n)
                }
                out.toString("UTF-8")
            }
        } finally { connection.disconnect() }
    }
    private fun requireSuccess(status:Int) {
        if(status !in 200..299)throw IOException(when(status) {
            403,429->"GitHub’s update service is busy. Try again later."
            404->"This update file is no longer available. Check for updates again."
            else->"Couldn’t reach the update service. Try again later."
        })
    }
    suspend fun latest():AppRelease? = withContext(Dispatchers.IO) {
        val json=text(UpdatePolicy.API_URL,512*1024,allowMissing=true) ?: return@withContext null
        val release=JSONObject(json)
        require(!release.optBoolean("draft") && !release.optBoolean("prerelease")) { "The release is not ready." }
        val assets=release.getJSONArray("assets")
        fun asset(name:String):JSONObject {
            val matches=(0 until assets.length()).map { assets.getJSONObject(it) }.filter { it.optString("name")==name }
            require(matches.size==1) { "The release is missing its $name file." }
            return matches.single()
        }
        val apk=asset("forma.apk");val metadata=asset("update.json")
        val metadataUrl=metadata.getString("browser_download_url")
        require(UpdatePolicy.releaseAssetUrl(metadataUrl)) { "Unexpected update metadata location." }
        UpdatePolicy.manifest(text(metadataUrl,64*1024)!!,apk.getString("browser_download_url"),apk.getLong("size"),release.getString("html_url"),release.optString("body"))
    }
    fun file(release:AppRelease)=File(context.cacheDir,"updates/forma-${release.versionCode}.apk")
    suspend fun verified(file:File,release:AppRelease) = withContext(Dispatchers.IO) {
        require(UpdatePolicy.newer(release.versionCode,BuildConfig.VERSION_CODE)) { "This update is already installed." }
        require(file.isFile && file.length()==release.bytes) { "The update download is incomplete." }
        val digest=MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer=ByteArray(8192);while(true) {
            currentCoroutineContext().ensureActive();val n=input.read(buffer);if(n<0)break;digest.update(buffer,0,n)
        } }
        val hex=digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        require(hex==release.sha256) { "Update integrity check failed. Download it again." }
        val manager=context.packageManager
        val archive=manager.getPackageArchiveInfo(file.absolutePath,PackageManager.GET_SIGNING_CERTIFICATES)
            ?: throw IllegalArgumentException("Android couldn’t verify this APK.")
        require(archive.packageName==context.packageName && archive.longVersionCode==release.versionCode.toLong()) { "The APK doesn’t match this Forma release." }
        val installed=manager.getPackageInfo(context.packageName,PackageManager.GET_SIGNING_CERTIFICATES).signingInfo
        val candidate=archive.signingInfo
        require(installed!=null && candidate!=null && !installed.hasMultipleSigners() && !candidate.hasMultipleSigners()) { "Unexpected APK signing identity." }
        val current=installed.apkContentsSigners.single().toByteArray()
        require(candidate.signingCertificateHistory.any { it.toByteArray().contentEquals(current) }) { "This APK wasn’t signed by Forma’s trusted signing key." }
    }
    suspend fun download(release:AppRelease,onProgress:(Float)->Unit):File = withContext(Dispatchers.IO) {
        val target=file(release);target.parentFile!!.mkdirs()
        val partial=File(target.parentFile,target.name+"."+java.util.UUID.randomUUID()+".part")
        val connection=connection(release.apkUrl)
        try {
            requireSuccess(connection.responseCode)
            val declared=connection.contentLengthLong
            require(declared<0 || declared==release.bytes) { "Unexpected APK file size." }
            connection.inputStream.use { input -> partial.outputStream().use { output ->
                val buffer=ByteArray(32*1024);var total=0L;var reported=-1
                while(true) {
                    currentCoroutineContext().ensureActive()
                    val n=input.read(buffer);if(n<0)break;total+=n
                    require(total<=release.bytes && total<=UpdatePolicy.MAX_APK_BYTES) { "The APK exceeds its expected size." }
                    output.write(buffer,0,n)
                    val percent=(total*100/release.bytes).toInt()
                    if(percent!=reported) { reported=percent;onProgress(total.toFloat()/release.bytes) }
                }
                require(total==release.bytes) { "The download was interrupted. Try again." }
            } }
            verified(partial,release)
            if(target.exists())target.delete()
            require(partial.renameTo(target)) { "Couldn’t save the update on this phone." }
            target.parentFile!!.listFiles()?.filter { it!=target && (it.extension=="apk" || it.lastModified()<System.currentTimeMillis()-TimeUnit.HOURS.toMillis(1)) }?.forEach { it.delete() }
            target
        } finally { connection.disconnect();partial.delete() }
    }
}

data class AppUpdateState(
    val automatic:Boolean=true,val release:AppRelease?=null,val checkedAt:Long=0,
    val checking:Boolean=false,val downloading:Boolean=false,val progress:Float=0f,
    val ready:Boolean=false,val error:String?=null,val hasChecked:Boolean=false,val noReleases:Boolean=false
)

class AppUpdateViewModel(app:Application):AndroidViewModel(app) {
    private val store=UpdateStore(app)
    private val service=GitHubUpdates(app)
    private fun local()=AppUpdateState(automatic=store.automatic(),release=store.release(),checkedAt=store.lastChecked(),
        ready=store.release()?.let { service.file(it).isFile } ?: false,hasChecked=store.lastChecked()>0,noReleases=!store.hasPublished())
    private val _state=MutableStateFlow(local())
    val state=_state.asStateFlow()
    private val _install=MutableSharedFlow<File>(extraBufferCapacity=1)
    val installation=_install.asSharedFlow()
    private var downloadJob:Job?=null
    private val listener=SharedPreferences.OnSharedPreferenceChangeListener { _,_ -> refreshLocal() }
    init { store.preferences.registerOnSharedPreferenceChangeListener(listener);schedule() }
    override fun onCleared() { store.preferences.unregisterOnSharedPreferenceChangeListener(listener);super.onCleared() }
    fun refreshLocal() { val saved=local();_state.update { it.copy(automatic=saved.automatic,release=saved.release,checkedAt=saved.checkedAt,ready=saved.ready,hasChecked=saved.hasChecked,noReleases=saved.noReleases) } }
    fun check(force:Boolean=false) {
        if(_state.value.checking || _state.value.downloading)return
        if(!force && (!store.automatic() || System.currentTimeMillis()-store.lastAttempt()<UpdatePolicy.FOREGROUND_INTERVAL_MS))return
        store.attempted();_state.update { it.copy(checking=true,error=null) }
        viewModelScope.launch {
            try { store.checked(service.latest());refreshLocal() }
            catch(e:CancellationException) { throw e }
            catch(e:Exception) { _state.update { it.copy(error=e.message ?: "Couldn’t check for updates. Your health data is still available.") } }
            finally { _state.update { it.copy(checking=false) } }
        }
    }
    fun automatic(value:Boolean) { store.setAutomatic(value);schedule();if(value)check() }
    private fun schedule() {
        val work=WorkManager.getInstance(getApplication())
        if(store.automatic())work.enqueueUniquePeriodicWork("forma-app-updates",ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<AppUpdateWorker>(6,TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.MINUTES).build())
        else work.cancelUniqueWork("forma-app-updates")
    }
    fun install() {
        val release=_state.value.release ?: return
        if(_state.value.downloading || _state.value.checking)return
        _state.update { it.copy(downloading=true,progress=0f,error=null) }
        downloadJob=viewModelScope.launch {
            try {
                val file=service.file(release)
                val ready=if(_state.value.ready) { service.verified(file,release);file }
                    else service.download(release) { progress -> _state.update { it.copy(progress=progress) } }
                _state.update { it.copy(ready=true,downloading=false,progress=1f) }
                _install.emit(ready)
            } catch(e:CancellationException) { throw e }
            catch(e:Exception) { service.file(release).delete();_state.update { it.copy(ready=false,error=e.message ?: "Couldn’t download the update. Try again.") } }
            finally { _state.update { it.copy(downloading=false) } }
        }
    }
    fun cancelDownload() { downloadJob?.cancel();_state.update { it.copy(downloading=false,progress=0f) } }
    fun installError(text:String) { _state.update { it.copy(error=text) } }
}

class AppUpdateWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result {
        val store=UpdateStore(applicationContext)
        if(!store.automatic() || System.currentTimeMillis()-store.lastAttempt()<UpdatePolicy.FOREGROUND_INTERVAL_MS)return Result.success()
        store.attempted()
        return try { val release=GitHubUpdates(applicationContext).latest();if(store.automatic())store.checked(release);Result.success() }
        catch(e:CancellationException) { throw e }
        catch(e:IOException) { Result.retry() }
        catch(e:Exception) { Result.failure() }
    }
}
