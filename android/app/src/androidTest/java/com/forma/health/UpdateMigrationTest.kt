package com.forma.health

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Explicit before/after probes for a real package upgrade, using synthetic local records. */
class UpdateMigrationTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun writeProbe() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("migrationProbe")=="true")
        File(context.filesDir,"ota-migration-probe.txt").writeText("local-records-survive")
        assertTrue(context.getSharedPreferences("ota-migration-probe",0).edit().putInt("hydration",1750).commit())
    }
    @Test fun readProbe() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("migrationProbe")=="true")
        assertEquals("local-records-survive",File(context.filesDir,"ota-migration-probe.txt").readText())
        assertEquals(1750,context.getSharedPreferences("ota-migration-probe",0).getInt("hydration",0))
        File(context.filesDir,"ota-migration-probe.txt").delete()
        context.getSharedPreferences("ota-migration-probe",0).edit().clear().commit()
    }
}
