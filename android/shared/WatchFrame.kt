package com.forma.health

import org.json.JSONObject
import java.time.*

data class WatchFrame(val device:String,val sentAt:Instant,val heart:Double?=null,val heartAt:Instant?=null,val steps:Double?=null,val stepsAt:Instant?=null,val calories:Double?=null,val caloriesAt:Instant?=null,val distanceKm:Double?=null,val distanceAt:Instant?=null,val floors:Double?=null,val floorsAt:Instant?=null) {
    fun currentHeart(now:Instant)=heart?.takeIf { heartAt!=null && Duration.between(heartAt,now).seconds in 0..30 }
    fun currentSteps(now:Instant,zone:ZoneId)=steps?.takeIf { stepsAt!=null && stepsAt.atZone(zone).toLocalDate()==now.atZone(zone).toLocalDate() && Duration.between(stepsAt,now).seconds in 0..300 }
    fun json():String=JSONObject().put("version",1).put("device",device).put("sentAt",sentAt.toString()).apply {
        fun metric(key:String,value:Double?,at:Instant?) { if(value!=null && at!=null) { put(key,value);put(key+"At",at.toString()) } }
        metric("heart",heart,heartAt);metric("steps",steps,stepsAt);metric("calories",calories,caloriesAt);metric("distanceKm",distanceKm,distanceAt);metric("floors",floors,floorsAt)
    }.toString()
    companion object {
        const val PATH="/forma/v1/frame"
        fun decode(bytes:ByteArray,now:Instant=Instant.now()):WatchFrame {
            require(bytes.size in 1..8192)
            val o=JSONObject(bytes.toString(Charsets.UTF_8));require(o.getInt("version")==1)
            val sent=Instant.parse(o.getString("sentAt"));require(sent<=now.plusSeconds(60) && sent>=now.minusSeconds(86400))
            fun metric(key:String,range:ClosedFloatingPointRange<Double>):Pair<Double?,Instant?> {
                if(!o.has(key))return null to null
                require(o.get(key) is Number);val value=o.getDouble(key);require(value.isFinite() && value in range)
                val at=Instant.parse(o.getString(key+"At"));require(at<=sent.plusSeconds(5) && at>=now.minusSeconds(172800))
                return value to at
            }
            val h=metric("heart",20.0..240.0);val s=metric("steps",0.0..200000.0);val c=metric("calories",0.0..30000.0);val d=metric("distanceKm",0.0..500.0);val f=metric("floors",0.0..1000.0)
            return WatchFrame(o.optString("device","Watch").take(100),sent,h.first,h.second,s.first,s.second,c.first,c.second,d.first,d.second,f.first,f.second)
        }
        /** Each metric is reconciled by its measurement time; a fresh HR frame cannot revive old steps. */
        fun merge(old:WatchFrame?,next:WatchFrame):WatchFrame {
            if(old==null || old.device!=next.device)return next
            fun newer(a:Instant?,b:Instant?)=b!=null && (a==null || b>=a)
            return next.copy(heart=if(newer(old.heartAt,next.heartAt))next.heart else old.heart,heartAt=maxOfNullable(old.heartAt,next.heartAt),steps=if(newer(old.stepsAt,next.stepsAt))next.steps else old.steps,stepsAt=maxOfNullable(old.stepsAt,next.stepsAt),calories=if(newer(old.caloriesAt,next.caloriesAt))next.calories else old.calories,caloriesAt=maxOfNullable(old.caloriesAt,next.caloriesAt),distanceKm=if(newer(old.distanceAt,next.distanceAt))next.distanceKm else old.distanceKm,distanceAt=maxOfNullable(old.distanceAt,next.distanceAt),floors=if(newer(old.floorsAt,next.floorsAt))next.floors else old.floors,floorsAt=maxOfNullable(old.floorsAt,next.floorsAt),sentAt=maxOf(old.sentAt,next.sentAt))
        }
        private fun maxOfNullable(a:Instant?,b:Instant?):Instant?=listOfNotNull(a,b).maxOrNull()
    }
}
