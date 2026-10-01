package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.core.AppLanguage
import com.example.ui.expert.ExpertViewModel
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
class ExpertViewModelTest {

    @Test
    fun testDefaultConfigState() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = ExpertViewModel(app)

        val cfg = vm.configState.value
        assertTrue(cfg.pingEnabled)
        assertTrue(cfg.dnsEnabled)
        assertTrue(cfg.httpEnabled)
        assertTrue(cfg.tcpEnabled)
        assertFalse(cfg.dohEnabled)
        assertFalse(cfg.proxyEnabled)
        assertFalse(cfg.speedEnabled)
        assertTrue(cfg.targetHosts.contains("8.8.8.8"))
    }

    @Test
    fun testToggleOptionsAndPresets() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = ExpertViewModel(app)

        vm.setPingEnabled(false)
        assertFalse(vm.configState.value.pingEnabled)

        vm.setDohEnabled(true)
        assertTrue(vm.configState.value.dohEnabled)

        vm.addTargetHost("custom.domain.com")
        assertTrue(vm.configState.value.targetHosts.contains("custom.domain.com"))

        vm.updateTimeoutMs(5000L)
        assertEquals(5000L, vm.configState.value.timeoutMs)
    }

    @Test
    fun testValidationWhenNoOptionsSelected() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = ExpertViewModel(app)

        vm.setPingEnabled(false)
        vm.setDnsEnabled(false)
        vm.setHttpEnabled(false)
        vm.setTcpEnabled(false)
        vm.setDohEnabled(false)
        vm.setProxyEnabled(false)
        vm.setSpeedEnabled(false)

        vm.startLocalDiagnosis(AppLanguage.CHINESE)
        assertNotNull(vm.executionState.value.errorMessage)
        assertFalse(vm.executionState.value.isRunning)
    }

    @Test
    fun testHttpCustomUrlsConfig() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = ExpertViewModel(app)

        vm.updateHttpUrls("https://my-service.com/health")
        assertEquals("https://my-service.com/health", vm.configState.value.httpUrls)

        vm.addHttpUrl("https://example.com/api")
        assertTrue(vm.configState.value.httpUrls.contains("https://example.com/api"))
    }

    @Test
    fun testProxyCustomDnsConfig() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = ExpertViewModel(app)

        assertEquals("SYSTEM", vm.configState.value.proxyDnsMode)
        vm.setProxyDnsMode("TRADITIONAL")
        assertEquals("TRADITIONAL", vm.configState.value.proxyDnsMode)

        vm.updateProxyDnsServer("114.114.114.114")
        assertEquals("114.114.114.114", vm.configState.value.proxyDnsServer)

        vm.setProxyDnsMode("DOH")
        assertEquals("DOH", vm.configState.value.proxyDnsMode)

        vm.updateProxyDohServer("https://dns.google/dns-query")
        assertEquals("https://dns.google/dns-query", vm.configState.value.proxyDohServer)
    }
}
