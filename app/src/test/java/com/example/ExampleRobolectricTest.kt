package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.sync.PreferencesManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Aroma POS", appName)
  }

  @Test
  fun `verify local admin credentials omini and admin116`() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = PreferencesManager(context)
    prefs.clearLocalSession()

    assertFalse(prefs.verifyAndSignInLocalAdmin("omini", "wrong"))
    assertNull(prefs.localSessionFlow.first())

    assertTrue(prefs.verifyAndSignInLocalAdmin("omini", "admin116"))
    val session = prefs.localSessionFlow.first()
    assertNotNull(session)
    assertEquals("omini", session?.displayName)
    assertEquals("admin", session?.roleKey)
  }
}
