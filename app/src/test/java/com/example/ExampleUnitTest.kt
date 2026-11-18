package com.example

import com.example.data.sync.LanSyncClient
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun lanSyncUrlNormalization_addsHttpAndTrimsSlash() {
    assertEquals("http://192.168.1.50:8787", LanSyncClient.normalizeServerUrl(" 192.168.1.50:8787/ "))
    assertEquals("http://10.0.2.2:8787", LanSyncClient.normalizeServerUrl("http://10.0.2.2:8787/"))
    assertEquals("", LanSyncClient.normalizeServerUrl("   "))
  }
}
