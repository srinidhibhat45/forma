package com.forma.health

import androidx.health.connect.client.*
import androidx.health.connect.client.changes.*
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.records.metadata.*
import androidx.health.connect.client.request.*
import androidx.health.connect.client.response.*
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy
import java.time.*

/** Exercises the real reader with SDK responses, without implying a physical Samsung sync was tested. */
class HealthReaderIntegrationTest {
    private val now=Instant.now()
    private val today=LocalDate.now()
    private val origin=DataOrigin("com.sec.android.app.shealth")
    private fun step(at:Instant)=StepsRecord(at.minusSeconds(60),null,at,null,100,Metadata.manualEntry())
    private fun reader(changes:Map<String,ChangesResponse> = emptyMap(),records:List<Record> = emptyList(),onRead:(ReadRecordsRequest<*>)->Unit={}):HealthReader {
        val permissions=setOf(HealthPermission.getReadPermission(SleepSessionRecord::class),HealthPermission.getReadPermission(HeartRateRecord::class))
        val controller=object:PermissionController {
            override suspend fun getGrantedPermissions()=permissions
            override suspend fun revokeAllPermissions()=Unit
        }
        val features=object:HealthConnectFeatures { override fun getFeatureStatus(feature:Int)=HealthConnectFeatures.FEATURE_STATUS_UNAVAILABLE }
        val fake=Proxy.newProxyInstance(HealthConnectClient::class.java.classLoader,arrayOf(HealthConnectClient::class.java)) { _,method,args->
            when(method.name) {
                "getPermissionController"->controller
                "getFeatures"->features
                "getChanges"->changes.getValue(args!![0] as String)
                "getChangesToken"->{ val request=args!![0] as ChangesTokenRequest;assertEquals(setOf(origin),request.dataOriginFilters);"checkpoint" }
                "aggregateGroupByDuration","aggregateGroupByPeriod"->emptyList<Any>()
                "readRecords"->{
                    val request=args!![0] as ReadRecordsRequest<*>;onRead(request)
                    assertEquals(setOf(origin),request.dataOriginFilter)
                    val selected=records.filter { request.recordType.isInstance(it) }.filter { r->
                        val at=when(r) { is SleepSessionRecord->r.endTime;is HeartRateRecord->r.endTime;else->now }
                        val range=request.timeRangeFilter;at>=range.startTime!! && at<range.endTime!!
                    }
                    // An empty final token is permitted by the API and must not trigger another read.
                    ReadRecordsResponse(selected,"")
                }
                else->error("Unexpected SDK call: ${method.name}")
            }
        } as HealthConnectClient
        return HealthReader(InstrumentationRegistry.getInstrumentation().targetContext,fake)
    }
    @Test fun changedRecentRecordsAvoidHistoricalScanButLaterPagesStillReconcileOldCorrections()=runBlocking {
        val first=ChangesResponse(listOf(UpsertionChange(step(now.minusSeconds(600)))),"next",true,false)
        val next=ChangesResponse(listOf(UpsertionChange(step(now.minus(Duration.ofDays(10))))),"end",false,false)
        assertEquals(ChangeCheck(true,true),reader(mapOf("start" to first,"next" to next)).changes("start"))
        assertEquals(ChangeCheck(true,false),reader(mapOf("start" to first,"next" to ChangesResponse(emptyList(),"end",false,false))).changes("start"))
    }
    @Test fun deletedAndExpiredChangesRequireReconciliation()=runBlocking {
        assertTrue(reader(mapOf("start" to ChangesResponse(listOf(DeletionChange("removed")),"end",false,false))).changes("start").historyChanged)
        assertTrue(reader(mapOf("start" to ChangesResponse(emptyList(),"end",false,true))).changes("start").historyChanged)
        assertEquals(ChangeCheck(false,false),reader(mapOf("start" to ChangesResponse(emptyList(),"end",false,false))).changes("start"))
    }
    @Test fun genericSleepAndCurrentHeartReachDifferentFieldsWithoutInventedBaselines()=runBlocking {
        val wake=today.atStartOfDay(ZoneId.systemDefault()).toInstant().plusSeconds(3600)
        val start=wake.minusSeconds(8*3600)
        val night=SleepSessionRecord(start,null,wake,null,Metadata.manualEntry(),stages=listOf(SleepSessionRecord.Stage(start,wake,SleepSessionRecord.STAGE_TYPE_SLEEPING)))
        val heartAt=now.minusSeconds(60)
        val heart=HeartRateRecord(heartAt.minusSeconds(1),null,heartAt.plusSeconds(1),null,listOf(HeartRateRecord.Sample(heartAt,88)),Metadata.manualEntry())
        var pages=0
        val result=reader(records=listOf<Record>(night,heart),onRead={pages++}).sync()
        val day=result.days.first { it.date==today }
        assertEquals(480.0,day["sleepMinutes"]!!,.001);assertEquals("Asleep",day.stages.single().stage)
        assertEquals(88.0,day["heartRateLatest"]!!,.001);assertEquals(heartAt,day.heartRecordedAt)
        assertNull(day["sleepHeartRate"]);assertNull(day["hrv"])
        assertTrue(result.report!!.historyComplete);assertTrue(pages<20)
    }
}
