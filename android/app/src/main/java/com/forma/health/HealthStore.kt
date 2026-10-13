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

class HealthStore(private val context: Context) {
    private val storage=context.getSharedPreferences("forma_private",Context.MODE_PRIVATE)
    fun health():HealthData?=storage.getString("health",null)?.let{runCatching{HealthJson.decode(it)}.getOrNull()}
    fun saveHealth(data:HealthData){storage.edit().putString("health",HealthJson.encode(data)).apply()}
    fun clearHealth(){storage.edit().remove("health").apply()}
    fun demo():HealthData {
        val d=HealthJson.decode(context.assets.open("demo.json").bufferedReader().use{it.readText()},true)
        val delta=java.time.temporal.ChronoUnit.DAYS.between(d.days.last().date,LocalDate.now())
        return d.copy(days=d.days.map{it.copy(date=it.date.plusDays(delta))})
    }
    fun prefs():Preferences=runCatching{val p=JSONObject(storage.getString("prefs","{}")!!);Preferences(p.optString("name","Your"),p.optInt("sleepGoal",480),p.optInt("stepGoal",10000),p.optInt("waterGoal",2500),p.optString("bedtime","23:00"),p.optBoolean("backgroundSync",false))}.getOrDefault(Preferences())
    fun savePrefs(p:Preferences){storage.edit().putString("prefs",JSONObject().put("name",p.name).put("sleepGoal",p.sleepGoal).put("stepGoal",p.stepGoal).put("waterGoal",p.waterGoal).put("bedtime",p.bedtime).put("backgroundSync",p.backgroundSync).toString()).apply()}
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
data class FormaState(val data:HealthData, val prefs:Preferences, val selected:LocalDate, val busy:Boolean=false, val water:Int=0,val journals:List<JournalEntry> = emptyList(),val workouts:List<ManualWorkout> = emptyList(),val message:String?=null)
class FormaViewModel(app:Application):AndroidViewModel(app) {
    val store=HealthStore(app);val reader=HealthReader(app)
    private val initial=store.health() ?: store.demo()
    private val _state=MutableStateFlow(FormaState(initial,store.prefs(),initial.days.last().date,water=store.water(initial.days.last().date),journals=store.journals(),workouts=store.workouts()))
    val state=_state.asStateFlow()
    private var syncJob:Job?=null
    fun refreshLocal(){val health=store.health()?:store.demo();_state.update{it.copy(data=health,prefs=store.prefs(),water=store.water(it.selected),journals=store.journals(),workouts=store.workouts())}}
    fun select(date:LocalDate){_state.update{it.copy(selected=date,water=store.water(date))}}
    fun message(text:String){_state.update{it.copy(message=text)}}
    fun clearMessage(){_state.update{it.copy(message=null)}}
    fun sync(){if(_state.value.busy)return;_state.update{it.copy(busy=true)};syncJob=viewModelScope.launch {
        try{val data=withContext(Dispatchers.IO){reader.sync(store.health())};store.saveHealth(data);_state.update{it.copy(data=data,selected=data.days.last().date,busy=false,message="Watch records updated",water=store.water(data.days.last().date))}}
        catch(e:CancellationException){throw e}
        catch(e:Exception){_state.update{it.copy(busy=false,message=e.message ?: "Sync unavailable. Please try again.")}}
    }}
    fun import(json:String){viewModelScope.launch{runCatching{withContext(Dispatchers.Default){HealthJson.decode(json)}}.onSuccess { data->store.saveHealth(data);_state.update{it.copy(data=data,selected=data.days.last().date,water=store.water(data.days.last().date),message="Imported ${data.days.size} days. Saved on this phone.")} }.onFailure{message(it.message ?: "Invalid file")}}}
    fun updatePrefs(p:Preferences){store.savePrefs(p);_state.update{it.copy(prefs=p)}}
    fun addWater(amount:Int){store.addWater(_state.value.selected,amount);refreshLocal()}
    fun saveJournal(mood:Int,habits:List<String>,note:String){store.saveJournal(JournalEntry(_state.value.selected,mood,habits,note.take(2000)));refreshLocal();message("Your check-in is saved")}
    fun saveWorkout(type:String,minutes:Int,intensity:String){require(minutes in 1..600);store.saveWorkout(ManualWorkout(UUID.randomUUID().toString(),_state.value.selected,type,minutes,intensity));refreshLocal();message("Activity saved in your journal")}
    fun deleteWorkout(id:String){store.deleteWorkout(id);refreshLocal()}
    fun clearAll(){syncJob?.cancel();_state.update{it.copy(busy=false)};WorkManager.getInstance(getApplication()).cancelUniqueWork("forma-health-sync");store.clearAll();refreshLocal();select(_state.value.data.days.last().date);message("Local data cleared")}
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
        val data=reader.sync(store.health());if(store.prefs().backgroundSync)store.saveHealth(data);Result.success()
    }catch(e:SecurityException){Result.success()}catch(e:Exception){Result.retry()} }
}
