package io.github.lilixp.utcradioclock.data.location

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import io.github.lilixp.utcradioclock.R
import io.github.lilixp.utcradioclock.domain.model.GeoPosition
import io.github.lilixp.utcradioclock.domain.model.LocationFix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.xmlpull.v1.XmlPullParser
import java.time.Instant

/** The last GPS position in position.xml (real SharedPreferences); a new store is the app after a restart. */
@RunWith(RobolectricTestRunner::class)
class SharedPreferencesLastPositionStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun restart() = SharedPreferencesLastPositionStore(context)

    private val boghiceni = LocationFix(GeoPosition(46.961234, 28.304149), 12.5f, Instant.parse("2026-09-30T15:40:00Z"))

    @Test
    fun nothingSaved_nothingLoaded() {
        assertNull(restart().load())
    }

    @Test
    fun savedAndReloaded_roundedToAbout100Metres() {
        restart().save(boghiceni)
        val loaded = restart().load()!!
        assertEquals(46.961, loaded.position.latitude, 1e-9)
        assertEquals(28.304, loaded.position.longitude, 1e-9)
        assertEquals(12.5f, loaded.accuracyMeters)
        assertEquals(boghiceni.time, loaded.time)
        assertFalse(loaded.approximate)
        assertNull(loaded.altitudeMeters) // none was given
    }

    @Test
    fun altitude_savedAndReloaded() {
        restart().save(boghiceni.copy(altitudeMeters = 144.6))
        assertEquals(144.6, restart().load()!!.altitudeMeters!!, 0.01)
        restart().save(boghiceni) // a later position without one: no old height left behind
        assertNull(restart().load()!!.altitudeMeters)
    }

    @Test
    fun southAndWest_andNoAccuracy_andApproximate() {
        restart().save(LocationFix(GeoPosition(-33.86882, -43.17291), null, Instant.EPOCH, approximate = true))
        val loaded = restart().load()!!
        assertEquals(-33.869, loaded.position.latitude, 1e-9)
        assertEquals(-43.173, loaded.position.longitude, 1e-9)
        assertNull(loaded.accuracyMeters)
        assertTrue(loaded.approximate)
    }

    @Test
    fun cleared_nothingLoaded() {
        restart().save(boghiceni)
        restart().clear()
        assertNull(restart().load())
    }

    @Test
    fun damagedFile_nothingLoaded() {
        context.getSharedPreferences("position", Context.MODE_PRIVATE).edit {
            putFloat("latitude", 123f)
            putFloat("longitude", 28f)
        }
        assertNull(restart().load())
    }

    /** The position must stay on this phone: the backup rules name only settings.xml. */
    @Test
    fun positionIsNotInTheBackups() {
        for (rules in listOf(R.xml.backup_rules, R.xml.data_extraction_rules)) {
            val parser = context.resources.getXml(rules)
            val included = mutableListOf<String>()
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "include") {
                    included += parser.getAttributeValue(null, "path")
                }
            }
            assertTrue(included.isNotEmpty())
            assertEquals(setOf("settings.xml"), included.toSet())
        }
    }
}
