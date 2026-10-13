package com.forma.health

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.HealthConnectFeatures
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.*
import java.time.*
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger

class HealthStore(private val context: Context) {
    private val storage=context.getSharedPreferences("forma_private",Context.MODE_PRIVATE)
    fun health():HealthData?=storage.getString("health",null)?.let{runCatching{HealthJson.decode(it)}.getOrNull()}
    fun saveHealth(data:HealthData){storage.edit().putString("health",HealthJson.encode(data)).apply()}
    fun clearHealth(){storage.edit().remove("health").remove("changesToken").remove("changesScope").apply()}
    fun checkpoint():Pair<String,String>? {
        val token=storage.getString("changesToken",null) ?: return null
        return token to storage.getString("changesScope","").orEmpty()
    }
    fun saveCheckpoint(token:String,scope:String){storage.edit().putString("changesToken",token).putString("changesScope",scope).apply()}
    fun demo():HealthData {
        val d=HealthJson.decode(context.assets.open("demo.json").bufferedReader().use{it.readText()},true)
        val delta=java.time.temporal.ChronoUnit.DAYS.between(d.days.last().date,LocalDate.now())
        return d.copy(days=d.days.map { day->fun shifted(at:Instant?)=at?.plus(Duration.ofDays(delta))
            day.copy(date=day.date.plusDays(delta),sleepStart=shifted(day.sleepStart),sleepEnd=shifted(day.sleepEnd),heartRecordedAt=shifted(day.heartRecordedAt),heartTimeline=day.heartTimeline.map { it.copy(at=shifted(it.at)!!) },activity=day.activity.map { it.copy(at=shifted(it.at)!!) },samples=day.samples.map { it.copy(recordedAt=shifted(it.recordedAt)) },recordedWorkouts=day.recordedWorkouts.map { w->w.copy(start=shifted(w.start)!!,end=shifted(w.end)!!,heart=w.heart.map { it.copy(at=shifted(it.at)!!) }) })
        })
    }
    fun prefs():Preferences=runCatching{val p=JSONObject(storage.getString("prefs","{}")!!);Preferences(p.optString("name","Your"),p.optInt("sleepGoal",480),p.optInt("stepGoal",10000),p.optInt("waterGoal",2500),p.optString("bedtime","23:00"),p.optBoolean("backgroundSync",false),p.optBoolean("automaticSync",true),p.optInt("calorieGoal",500).coerceIn(100,2000),p.optInt("exerciseGoal",30).coerceIn(5,180))}.getOrDefault(Preferences())
    fun savePrefs(p:Preferences){storage.edit().putString("prefs",JSONObject().put("name",p.name).put("sleepGoal",p.sleepGoal).put("stepGoal",p.stepGoal).put("waterGoal",p.waterGoal).put("bedtime",p.bedtime).put("backgroundSync",p.backgroundSync).put("automaticSync",p.automaticSync).put("calorieGoal",p.calorieGoal).put("exerciseGoal",p.exerciseGoal).toString()).apply()}
    fun water(date:LocalDate)=storage.getInt("water_$date",0)
    fun addWater(date:LocalDate,amount:Int){storage.edit().putInt("water_$date",(water(date)+amount).coerceAtLeast(0)).apply()}
    fun journals():List<JournalEntry> = runCatching { val a=JSONArray(storage.getString("journals","[]"));(0 until a.length()).map{val e=a.getJSONObject(it);val h=e.getJSONArray("habits");JournalEntry(LocalDate.parse(e.getString("date")),e.getInt("mood"),(0 until h.length()).map(h::getString),e.getString("note"))} }.getOrDefault(emptyList())
    fun saveJournal(e:JournalEntry){val list=journals().filter{it.date!=e.date}+e;storage.edit().putString("journals",JSONArray(list.map{JSONObject().put("date",it.date.toString()).put("mood",it.mood).put("habits",JSONArray(it.habits)).put("note",it.note)}).toString()).apply()}
    fun workouts():List<ManualWorkout> = runCatching { val a=JSONArray(storage.getString("workouts","[]"));(0 until a.length()).map{val w=a.getJSONObject(it);ManualWorkout(w.getString("id"),LocalDate.parse(w.getString("date")),w.getString("type"),w.getInt("minutes"),w.getString("intensity"))} }.getOrDefault(emptyList())
    fun saveWorkout(w:ManualWorkout){writeWorkouts(workouts()+w)}
    fun deleteWorkout(id:String){writeWorkouts(workouts().filter{it.id!=id})}
    private fun writeWorkouts(ws:List<ManualWorkout>){storage.edit().putString("workouts",JSONArray(ws.map{JSONObject().put("id",it.id).put("date",it.date.toString()).put("type",it.type).put("minutes",it.minutes).put("intensity",it.intensity)}).toString()).apply()}
    fun clearAll(){storage.edit().clear().apply()}
}
object HealthSyncCoordinator {
    val lock=Mutex()
    val epoch=AtomicInteger()
    suspend fun run(store:HealthStore,reader:HealthReader,fullHistory:Boolean=false,onProgress:(SyncProgress)->Unit={},onData:(HealthData)->Unit={},isAllowed:()->Boolean={true}):HealthData = lock.withLock {
        currentCoroutineContext().ensureActive()
        if(!isAllowed())throw CancellationException("Health refresh was paused")
        val epochAtStart=epoch.get()
        val scope=try { reader.changeScope() }catch(e:CancellationException){throw e}catch(e:Exception){null}
        val token=try { reader.changeToken() }catch(e:CancellationException){throw e}catch(e:Exception){null}
        val result=reader.sync(store.health(),fullHistory,onProgress) { data->
            currentCoroutineContext().ensureActive()
            if(epochAtStart!=epoch.get() || !isAllowed())throw CancellationException("Local health records were cleared or refresh paused")
            store.saveHealth(data);onData(data)
        }
        currentCoroutineContext().ensureActive()
        if(epochAtStart!=epoch.get() || !isAllowed())throw CancellationException("Local health records were cleared or refresh paused")
        if(token!=null && scope!=null)store.saveCheckpoint(token,scope)
        result
    }
}
data class FormaState(val data:HealthData, val prefs:Preferences, val selected:LocalDate, val busy:Boolean=false, val water:Int=0,val journals:List<JournalEntry> = emptyList(),val workouts:List<ManualWorkout> = emptyList(),val message:String?=null,val connection:ConnectionState=ConnectionState(),val syncProgress:SyncProgress=SyncProgress(),val watch:WatchLinkState=WatchLinkState())
class FormaViewModel(app:Application):AndroidViewModel(app) {
    val store=HealthStore(app);val reader=HealthReader(app)
    private val initial=store.health() ?: store.demo()
    private val _state=MutableStateFlow(FormaState(initial,store.prefs(),if(initial.demo)initial.days.last().date else LocalDate.now(),water=store.water(if(initial.demo)initial.days.last().date else LocalDate.now()),journals=store.journals(),workouts=store.workouts()))
    val state=_state.asStateFlow()
    init { WatchLink.load(app);viewModelScope.launch { WatchLink.state.collect { watch->_state.update { it.copy(watch=watch) } } } }
    fun enableWatch(enabled:Boolean) { WatchLink.enable(getApplication(),enabled);if(enabled)findWatch() }
    fun findWatch() { viewModelScope.launch { WatchLink.discover(getApplication()) } }
    private var syncJob:Job?=null
    fun refreshLocal(){val health=store.health()?:store.demo();_state.update{it.copy(data=health,prefs=store.prefs(),water=store.water(it.selected),journals=store.journals(),workouts=store.workouts())}}
    fun select(date:LocalDate){_state.update{it.copy(selected=date,water=store.water(date))}}
    fun message(text:String){_state.update{it.copy(message=text)}}
    fun clearMessage(){_state.update{it.copy(message=null)}}
    fun refreshConnection(){viewModelScope.launch { try { val connection=withContext(Dispatchers.IO){reader.connection()};_state.update { it.copy(connection=connection) } }catch(e:CancellationException){throw e}catch(e:Exception){message("Couldn’t check Health Connect. Open connections to retry.")} }}
    @Volatile private var syncGeneration=0
    suspend fun refreshIfChanged(){
        if(_state.value.busy || !store.prefs().automaticSync)return
        val connection=reader.connection();_state.update { it.copy(connection=connection) }
        if(!connection.hasReadAccess)return
        val saved=store.health();val checkpoint=store.checkpoint()
        val incomplete=saved?.report?.historyComplete!=true || saved.report.dataVersion!=2
        val newDay=saved?.syncedAt?.atZone(ZoneId.systemDefault())?.toLocalDate()!=LocalDate.now()
        val scopeChanged=checkpoint==null || checkpoint.second!=reader.changeScope()
        if(incomplete || newDay || scopeChanged) { sync(automatic=true);return }
        // Changed/deleted history may be older than the recent window. Reconcile all allowed days.
        val changes=try { reader.changes(checkpoint!!.first) }catch(e:CancellationException){throw e}catch(e:Exception){ChangeCheck(true,true)}
        if(changes.changed)sync(fullHistory=changes.historyChanged,automatic=true)
    }
    fun sync(fullHistory:Boolean=false,automatic:Boolean=false){
        if(_state.value.busy)return
        if(!automatic && !_state.value.prefs.automaticSync)updatePrefs(_state.value.prefs.copy(automaticSync=true))
        val generation=++syncGeneration
        _state.update{it.copy(busy=true,syncProgress=SyncProgress("Reading recent records"))}
        syncJob=viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { HealthSyncCoordinator.run(store,reader,fullHistory,
                    onProgress={ p->if(generation==syncGeneration)_state.update { it.copy(syncProgress=p) } },
                    onData={ data->if(generation==syncGeneration)_state.update { val selected=if(it.data.source!=data.source)LocalDate.now()else it.selected;it.copy(data=data,selected=selected,water=store.water(selected)) } }) }
                if(generation==syncGeneration)_state.update { it.copy(message=if(automatic)it.message else "Samsung Health records refreshed") }
            }catch(e:CancellationException){throw e}
            catch(e:Exception){if(generation==syncGeneration){refreshLocal();message(e.message ?: "Sync paused. Saved records are available; retry to continue history import.")}}
            finally { if(generation==syncGeneration)_state.update { it.copy(busy=false) };refreshConnection() }
        }
    }
    fun cancelSync(){
        ++syncGeneration;syncJob?.cancel();HealthSyncCoordinator.epoch.incrementAndGet()
        updatePrefs(_state.value.prefs.copy(automaticSync=false,backgroundSync=false))
        WorkManager.getInstance(getApplication()).cancelUniqueWork("forma-health-sync")
        _state.update { it.copy(busy=false,syncProgress=SyncProgress("History paused · tap Sync to continue")) }
    }
    fun import(json:String){viewModelScope.launch{runCatching{withContext(Dispatchers.Default){HealthJson.decode(json).let { it.copy(source="Imported · ${it.source}".take(120),report=null) }}}.onSuccess { data->WatchLink.enable(getApplication(),false);HealthSyncCoordinator.epoch.incrementAndGet();cancelSync();enableBackground(false);updatePrefs(_state.value.prefs.copy(automaticSync=false));store.clearHealth();store.saveHealth(data);_state.update{it.copy(data=data,selected=data.days.last().date,water=store.water(data.days.last().date),message="Imported ${data.days.size} days. Automatic health reads paused; tap Sync to resume Samsung records.")} }.onFailure{message(it.message ?: "Invalid file")}}}
    fun updatePrefs(p:Preferences){store.savePrefs(p);_state.update{it.copy(prefs=p)}}
    fun addWater(amount:Int){store.addWater(_state.value.selected,amount);refreshLocal()}
    fun saveJournal(mood:Int,habits:List<String>,note:String){store.saveJournal(JournalEntry(_state.value.selected,mood,habits,note.take(2000)));refreshLocal();message("Your check-in is saved")}
    fun saveWorkout(type:String,minutes:Int,intensity:String){require(minutes in 1..600);store.saveWorkout(ManualWorkout(UUID.randomUUID().toString(),_state.value.selected,type,minutes,intensity));refreshLocal();message("Activity saved in your journal")}
    fun deleteWorkout(id:String){store.deleteWorkout(id);refreshLocal()}
    fun clearAll(){WatchLink.enable(getApplication(),false);HealthSyncCoordinator.epoch.incrementAndGet();cancelSync();_state.update{it.copy(busy=false)};WorkManager.getInstance(getApplication()).cancelUniqueWork("forma-health-sync");store.clearAll();store.savePrefs(Preferences(automaticSync=false));refreshLocal();select(_state.value.data.days.last().date);message("Local data cleared. Tap Sync when you want to import records again.")}
    fun enableBackground(enable:Boolean){
        updatePrefs(_state.value.prefs.copy(backgroundSync=enable))
        val work=WorkManager.getInstance(getApplication())
        if(enable)work.enqueueUniquePeriodicWork("forma-health-sync",ExistingPeriodicWorkPolicy.UPDATE,PeriodicWorkRequestBuilder<HealthSyncWorker>(15,TimeUnit.MINUTES).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,5,TimeUnit.MINUTES).build()) else work.cancelUniqueWork("forma-health-sync")
    }
}
class HealthSyncWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params){
    override suspend fun doWork():Result { return try{
        val store=HealthStore(applicationContext);val reader=HealthReader(applicationContext)
        if(!store.prefs().backgroundSync)return Result.success()
        if(reader.status!=androidx.health.connect.client.HealthConnectClient.SDK_AVAILABLE)return Result.success()
        if(HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND !in reader.client.permissionController.getGrantedPermissions())return Result.success()
        HealthSyncCoordinator.run(store,reader,isAllowed={store.prefs().backgroundSync});Result.success()
    }catch(e:CancellationException){throw e}catch(e:SecurityException){Result.success()}catch(e:Exception){Result.retry()} }
}
