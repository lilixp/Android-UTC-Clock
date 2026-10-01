package io.github.lilixp.utcradioclock.data.location

import android.Manifest
import android.app.Application
import android.location.Location
import android.location.LocationManager
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper
import java.time.Instant

/** The Android side, on Robolectric's simulated LocationManager: permissions and providers, no real GPS. */
@RunWith(RobolectricTestRunner::class)
class AndroidLocationProviderTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(LocationManager::class.java)
    private val provider = AndroidLocationProvider(context)

    @Before
    fun locationOn() {
        shadowOf(manager).setLocationEnabled(true)
        shadowOf(manager).setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        shadowOf(manager).setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
    }

    private fun allow(vararg permissions: String) = shadowOf(context).grantPermissions(*permissions)

    private fun location(provider: String, latitude: Double, longitude: Double, time: Long, accuracy: Float = 20f) =
        Location(provider).apply {
            this.latitude = latitude
            this.longitude = longitude
            this.time = time
            this.accuracy = accuracy
        }

    @Test
    fun noPermission_nothingIsRead() = runTest {
        shadowOf(manager).simulateLocation(location(LocationManager.GPS_PROVIDER, 46.9612, 28.3041, 1_000))
        assertFalse(provider.hasPermission())
        assertNull(provider.lastKnown())
        assertNull(provider.currentLocation())
    }

    @Test
    fun approximateLocationOnly_isEnough_andMarkedApproximate() {
        allow(Manifest.permission.ACCESS_COARSE_LOCATION)
        shadowOf(manager).simulateLocation(location(LocationManager.NETWORK_PROVIDER, 46.96, 28.30, 1_000))
        assertTrue(provider.hasPermission())
        val fix = provider.lastKnown()!!
        assertTrue(fix.approximate)
        assertEquals(46.96, fix.position.latitude, 1e-9)
    }

    @Test
    fun preciseLocation_notApproximate_withAccuracyAndTime() {
        allow(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        shadowOf(manager).simulateLocation(location(LocationManager.GPS_PROVIDER, 46.9612, 28.3041, 1_000, accuracy = 8f))
        val fix = provider.lastKnown()!!
        assertFalse(fix.approximate)
        assertEquals(8f, fix.accuracyMeters)
        assertEquals(Instant.ofEpochMilli(1_000), fix.time)
        assertEquals(28.3041, fix.position.longitude, 1e-9)
    }

    @Test
    fun lastKnown_theNewestOfAllProviders() {
        allow(Manifest.permission.ACCESS_FINE_LOCATION)
        shadowOf(manager).simulateLocation(location(LocationManager.GPS_PROVIDER, 51.5, -0.12, 5_000))
        shadowOf(manager).simulateLocation(location(LocationManager.NETWORK_PROVIDER, 46.96, 28.30, 9_000))
        assertEquals(46.96, provider.lastKnown()!!.position.latitude, 1e-9)
    }

    @Test
    fun locationTurnedOff_isSeen() {
        assertTrue(provider.isLocationEnabled())
        shadowOf(manager).setLocationEnabled(false)
        assertFalse(provider.isLocationEnabled())
    }

    @Test
    fun currentLocation_oneNewPosition() = runTest {
        allow(Manifest.permission.ACCESS_FINE_LOCATION)
        val answer = async { provider.currentLocation() }
        testScheduler.runCurrent() // the request is made
        shadowOf(manager).simulateLocation(location(LocationManager.NETWORK_PROVIDER, 46.9612, 28.3041, 2_000))
        ShadowLooper.idleMainLooper()
        assertEquals(46.9612, answer.await()!!.position.latitude, 1e-9)
    }

    @Test
    fun allProvidersOff_noPosition() = runTest {
        allow(Manifest.permission.ACCESS_FINE_LOCATION)
        shadowOf(manager).setProviderEnabled(LocationManager.GPS_PROVIDER, false)
        shadowOf(manager).setProviderEnabled(LocationManager.NETWORK_PROVIDER, false)
        shadowOf(manager).setProviderEnabled(LocationManager.FUSED_PROVIDER, false)
        assertNull(provider.currentLocation())
    }
}
