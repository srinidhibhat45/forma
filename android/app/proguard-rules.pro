# Health Connect and WorkManager ship consumer rules; keep the worker constructor for scheduling.
-keep class com.forma.health.HealthSyncWorker { public <init>(...); }

-keep class com.forma.health.AppUpdateWorker { public <init>(...); }
