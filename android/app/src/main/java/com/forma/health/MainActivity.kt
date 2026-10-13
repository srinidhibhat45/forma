package com.forma.health

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class MainActivity:ComponentActivity(){
    private val model:FormaViewModel by viewModels()
    private val updates:AppUpdateViewModel by viewModels()
    private val installAccess=registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if(packageManager.canRequestPackageInstalls())updates.install()
        else updates.installError("Allow Forma to install its updates, then tap Install update again.")
    }
    private var backgroundRequest=false
    private val permissionLauncher=registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        if(backgroundRequest){backgroundRequest=false;val enabled=HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND in granted;model.enableBackground(enabled);model.message(if(enabled)"Background sync enabled; Android schedules updates approximately every 15 minutes." else "Background access wasn’t granted. Foreground sync remains available.")}
        else if(granted.isNotEmpty())model.sync() else model.message("Read permission wasn’t granted. You can still explore the demo or import a file.")
    }
    private val importLauncher=registerForActivityResult(ActivityResultContracts.OpenDocument()){uri->uri?.let{
        lifecycleScope.launch { try { val json=contentResolver.openInputStream(it)?.use{stream->val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);var count=stream.read(buffer);while(count!=-1){require(out.size()+count<=5*1024*1024){"Choose a JSON file smaller than 5 MB."};out.write(buffer,0,count);count=stream.read(buffer)};out.toString("UTF-8")};if(json!=null)model.import(json) }catch(e:Exception){model.message(e.message?:"Couldn’t read this file")} }
    }}
    private val exportLauncher=registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->uri?.let{try{contentResolver.openOutputStream(it)?.bufferedWriter()?.use{w->w.write(HealthJson.encode(model.state.value.data))};model.message("Health records exported")}catch(e:Exception){model.message("Couldn’t save the file")}}}
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState);enableEdgeToEdge(statusBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),navigationBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        val privacy=intent.action=="androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE" || intent.action=="android.intent.action.VIEW_PERMISSION_USAGE"
        setContent { val updateState by updates.state.collectAsStateWithLifecycle(); FormaApp(model,onConnect={requestHealth()},onBackground={requestBackground()},onImport={importLauncher.launch(arrayOf("application/json","text/plain"))},onExport={exportLauncher.launch("forma-health-records.json")},showPrivacy=privacy,updateState=updateState,onCheckUpdates={updates.check(force=true)},onInstallUpdate=updates::install,onCancelUpdate=updates::cancelDownload,onAutomaticUpdates=updates::automatic) }
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.RESUMED) { updates.installation.collect { launchInstaller(it) } } }
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED){model.refreshLocal();updates.refreshLocal();while(true){updates.check();if(!model.state.value.data.demo)model.sync();delay(5*60*1000)}} }
    }
    private fun launchInstaller(file:java.io.File) {
        try {
            if(!packageManager.canRequestPackageInstalls()) {
                installAccess.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:$packageName")))
                return
            }
            val uri=FileProvider.getUriForFile(this,"$packageName.updates",file)
            startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply { clipData=android.content.ClipData.newRawUri("Forma update",uri) })
        } catch(e:Exception) { updates.installError("Couldn’t open Android’s installer. Try Install update again.") }
    }
    private fun requestHealth(){
        when(model.reader.status){
            HealthConnectClient.SDK_AVAILABLE->permissionLauncher.launch(model.reader.permissions())
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED->model.message("Install or update Health Connect, then return to Forma. On Android 14+, find it in phone Settings.")
            else->model.message("Health Connect requires a compatible Android phone.")
        }
    }
    private fun requestBackground(){
        if(model.reader.status!=HealthConnectClient.SDK_AVAILABLE){model.message("Connect Health Connect first.");return}
        if(model.reader.client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND)!=HealthConnectFeatures.FEATURE_STATUS_AVAILABLE){model.message("Background read isn’t supported by this version of Health Connect.");return}
        backgroundRequest=true;permissionLauncher.launch(setOf(HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND))
    }
}
