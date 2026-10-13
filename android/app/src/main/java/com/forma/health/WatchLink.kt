package com.forma.health

import android.content.Context
import com.google.android.gms.wearable.*
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.util.concurrent.TimeUnit

data class WatchLinkState(val enabled:Boolean=false,val connected:Boolean=false,val device:String?=null,val frame:WatchFrame?=null,val error:String?=null)
/** Google Play services confines Data Layer messages to matching package names and signing keys. */
object WatchLink {
    private val mutable=MutableStateFlow(WatchLinkState())
    val state=mutable.asStateFlow()
    private fun prefs(c:Context)=c.getSharedPreferences("forma_watch_link",Context.MODE_PRIVATE)
    @Synchronized fun load(c:Context) {
        val enabled=prefs(c).getBoolean("watchEnabled",false)
        val frame=if(enabled)prefs(c).getString("watchFrame",null)?.let { runCatching { WatchFrame.decode(it.toByteArray()) }.getOrNull() }else null
        mutable.value=mutable.value.copy(enabled=enabled,frame=frame)
    }
    @Synchronized fun enable(c:Context,on:Boolean) {
        prefs(c).edit().putBoolean("watchEnabled",on).apply()
        if(!on)prefs(c).edit().remove("watchFrame").apply()
        mutable.value=WatchLinkState(enabled=on)
    }
    suspend fun discover(c:Context) {
        if(!mutable.value.enabled)return
        try {
            val capability=Wearable.getCapabilityClient(c).getCapability("forma_watch_v1",CapabilityClient.FILTER_REACHABLE).await()
            val node=capability.nodes.filter { it.isNearby }.sortedBy { it.id }.firstOrNull()
            if(mutable.value.enabled)mutable.value=mutable.value.copy(connected=node!=null,device=node?.displayName,error=null)
        } catch(e:kotlinx.coroutines.CancellationException) { throw e }
        catch(e:Exception) { if(mutable.value.enabled)mutable.value=mutable.value.copy(connected=false,error="Watch connection unavailable. Check Galaxy Wearable and open Forma on your watch.") }
    }
    @Synchronized fun receive(c:Context,bytes:ByteArray):Boolean {
        if(!prefs(c).getBoolean("watchEnabled",false))return false
        val next=runCatching { WatchFrame.decode(bytes) }.getOrNull() ?: return false
        val merged=WatchFrame.merge(mutable.value.frame,next)
        prefs(c).edit().putString("watchFrame",merged.json()).apply()
        mutable.value=mutable.value.copy(enabled=true,connected=true,device=next.device,frame=merged,error=null)
        return true
    }
}
class WatchMessageService:WearableListenerService() {
    override fun onMessageReceived(event:MessageEvent) {
        if(event.path!=WatchFrame.PATH || event.data.size>8192 || !getSharedPreferences("forma_watch_link",MODE_PRIVATE).getBoolean("watchEnabled",false))return
        // Nearby matching Forma capability excludes delayed cloud-only/off-device senders.
        val nodes=runCatching { Tasks.await(Wearable.getCapabilityClient(this).getCapability("forma_watch_v1",CapabilityClient.FILTER_REACHABLE),5,TimeUnit.SECONDS).nodes }.getOrNull() ?: return
        if(nodes.none { it.id==event.sourceNodeId && it.isNearby })return
        WatchLink.receive(this,event.data)
    }
}
