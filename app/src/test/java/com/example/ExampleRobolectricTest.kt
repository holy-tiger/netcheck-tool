package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.Base64Decoder
import com.example.core.SystemUtils
import org.junit.Assert.assertEquals
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
    assertEquals("NetCheck", appName)
  }

  @Test
  fun `tracking id contains valid characters and has length 6`() {
    val id = SystemUtils.generateTrackingId()
    assertEquals(6, id.length)
    assertTrue(id.matches(Regex("^[A-HJ-NP-Z2-9]{6}$")))
  }

  @Test
  fun `base64 decode with unpadded and whitespace string`() {
    val raw = "  eyJyZXBvcnRfdXJsIjoidGVzdCJ9  "
    val encoded = android.util.Base64.encodeToString(raw.toByteArray(), android.util.Base64.NO_WRAP)
    // Strip trailing equals to test auto-padding and add newline
    val unpadded = encoded.trimEnd('=') + "\n "
    val decoded = Base64Decoder.decode(unpadded)
    assertEquals(raw.trim(), decoded.trim())
  }
}

