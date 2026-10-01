package com.example.ui.expert

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.AnalysisReport
import com.example.core.AppLanguage
import com.example.core.TaskExecutionResult

@Composable
fun ExpertScreen(
    viewModel: ExpertViewModel,
    currentLanguage: AppLanguage,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configState by viewModel.configState.collectAsStateWithLifecycle()
    val executionState by viewModel.executionState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    BackHandler {
        focusManager.clearFocus()
        keyboardController?.hide()
        if (executionState.isCompleted) {
            viewModel.resetExecution()
        } else {
            onNavigateBack()
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                focusManager.clearFocus()
                keyboardController?.hide()
            }
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Local-Only Privacy Guarantee Banner
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF0284C7).copy(alpha = 0.08f),
            border = BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = Color(0xFF0284C7),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.local_mode_notice),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Medium,
                        lineHeight = 18.sp
                    ),
                    color = Color(0xFF0369A1),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (executionState.isRunning) {
            // Running Progress View
            ExpertRunningCard(executionState = executionState)
        } else if (executionState.isCompleted) {
            // Completed Results View (Local only)
            ExpertResultsView(
                executionState = executionState,
                onRetest = { viewModel.resetExecution() }
            )
        } else {
            // Configuration & Target Input Form
            ExpertConfigForm(
                configState = configState,
                errorMessage = executionState.errorMessage,
                viewModel = viewModel,
                currentLanguage = currentLanguage
            )
        }

        Spacer(modifier = Modifier.height(28.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpertConfigForm(
    configState: ExpertConfigState,
    errorMessage: String?,
    viewModel: ExpertViewModel,
    currentLanguage: AppLanguage
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val presetTargets = listOf("8.8.8.8", "1.1.1.1", "baidu.com", "google.com", "cloudflare.com", "111.91.2.32")

    // Error Alert if any
    if (!errorMessage.isNullOrBlank()) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFEF4444).copy(alpha = 0.12f),
            border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFDC2626), fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    // 1. Target Hosts Input Card
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                focusManager.clearFocus()
                keyboardController?.hide()
            },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.target_hosts_label),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = configState.targetHosts,
                onValueChange = { viewModel.updateTargetHosts(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 90.dp, max = 150.dp)
                    .testTag("expert_targets_input"),
                placeholder = { Text(stringResource(R.string.target_hosts_hint), style = MaterialTheme.typography.bodySmall) },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, lineHeight = 18.sp),
                trailingIcon = {
                    if (configState.targetHosts.isNotEmpty()) {
                        IconButton(onClick = {
                            viewModel.updateTargetHosts("")
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear_button))
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Presets Chips
            Text(
                text = stringResource(R.string.preset_targets_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presetTargets.forEach { preset ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.clickable {
                            viewModel.addTargetHost(preset)
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        }
                    ) {
                        Text(
                            text = preset,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }

    // 2. Selectable Probe Options List
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                focusManager.clearFocus()
                keyboardController?.hide()
            },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val primaryAccent = MaterialTheme.colorScheme.primary

            Text(
                text = stringResource(R.string.expert_select_options_title),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            // Probe 1: Ping
            OptionRowCard(
                title = stringResource(R.string.opt_ping),
                description = stringResource(R.string.desc_ping),
                icon = Icons.Default.Wifi,
                checked = configState.pingEnabled,
                onCheckedChange = { viewModel.setPingEnabled(it) },
                accentColor = primaryAccent
            )

            // Probe 2: DNS
            OptionRowCard(
                title = stringResource(R.string.opt_dns),
                description = stringResource(R.string.desc_dns),
                icon = Icons.Default.Language,
                checked = configState.dnsEnabled,
                onCheckedChange = { viewModel.setDnsEnabled(it) },
                accentColor = primaryAccent
            )

            // Probe 3: HTTP
            OptionRowCard(
                title = stringResource(R.string.opt_http),
                description = stringResource(R.string.desc_http),
                icon = Icons.Default.Link,
                checked = configState.httpEnabled,
                onCheckedChange = { viewModel.setHttpEnabled(it) },
                accentColor = primaryAccent
            )

            // HTTP Dedicated Subpanel
            AnimatedVisibility(
                visible = configState.httpEnabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = primaryAccent.copy(alpha = 0.04f),
                    border = BorderStroke(1.dp, primaryAccent.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        }
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = stringResource(R.string.http_params_title),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = primaryAccent)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = stringResource(R.string.http_urls_label),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = configState.httpUrls,
                            onValueChange = { viewModel.updateHttpUrls(it) },
                            placeholder = { Text(stringResource(R.string.http_urls_hint), style = MaterialTheme.typography.bodySmall) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.expert_quick_fill), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(8.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(
                                    "百度" to "https://www.baidu.com",
                                    "Google" to "https://www.google.com",
                                    "Cloudflare" to "https://www.cloudflare.com"
                                ).forEach { (label, url) ->
                                    val isCurrent = configState.httpUrls.contains(url)
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isCurrent) primaryAccent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, if (isCurrent) primaryAccent else MaterialTheme.colorScheme.outlineVariant),
                                        modifier = Modifier.clickable {
                                            viewModel.addHttpUrl(url)
                                            focusManager.clearFocus()
                                            keyboardController?.hide()
                                        }
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = if (isCurrent) primaryAccent else MaterialTheme.colorScheme.onSurface,
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                                            ),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Probe 4: TCP
            OptionRowCard(
                title = stringResource(R.string.opt_tcp),
                description = stringResource(R.string.desc_tcp),
                icon = Icons.Default.SettingsEthernet,
                checked = configState.tcpEnabled,
                onCheckedChange = { viewModel.setTcpEnabled(it) },
                accentColor = primaryAccent
            )

            // Probe 5: DoH
            OptionRowCard(
                title = stringResource(R.string.opt_doh),
                description = stringResource(R.string.desc_doh),
                icon = Icons.Default.Security,
                checked = configState.dohEnabled,
                onCheckedChange = { viewModel.setDohEnabled(it) },
                accentColor = primaryAccent
            )

            // DoH Dedicated Subpanel
            AnimatedVisibility(
                visible = configState.dohEnabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = primaryAccent.copy(alpha = 0.04f),
                    border = BorderStroke(1.dp, primaryAccent.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = stringResource(R.string.expert_doh_params_title),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = primaryAccent)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(stringResource(R.string.doh_servers_label), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = configState.dohServers,
                            onValueChange = { viewModel.updateDohServers(it) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.expert_quick_fill), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(8.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(
                                    "Cloudflare" to "https://cloudflare-dns.com/dns-query",
                                    "Google" to "https://dns.google/dns-query",
                                    "阿里" to "https://dns.alidns.com/dns-query"
                                ).forEach { (label, url) ->
                                    val isCurrent = configState.dohServers.contains(url)
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isCurrent) primaryAccent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, if (isCurrent) primaryAccent else MaterialTheme.colorScheme.outlineVariant),
                                        modifier = Modifier.clickable { viewModel.addDohServer(url) }
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = if (isCurrent) primaryAccent else MaterialTheme.colorScheme.onSurface,
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                                            ),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(stringResource(R.string.doh_domains_label), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = configState.dohDomains,
                            onValueChange = { viewModel.updateDohDomains(it) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }

            // Probe 6: Proxy
            OptionRowCard(
                title = stringResource(R.string.opt_proxy),
                description = stringResource(R.string.desc_proxy),
                icon = Icons.Default.Dns,
                checked = configState.proxyEnabled,
                onCheckedChange = { viewModel.setProxyEnabled(it) },
                accentColor = primaryAccent
            )

            // Proxy Dedicated Subpanel
            AnimatedVisibility(
                visible = configState.proxyEnabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = primaryAccent.copy(alpha = 0.04f),
                    border = BorderStroke(1.dp, primaryAccent.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = stringResource(R.string.expert_proxy_params_title),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = primaryAccent)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(stringResource(R.string.proxy_urls_label), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = configState.proxyUrls,
                            onValueChange = { viewModel.updateProxyUrls(it) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            shape = RoundedCornerShape(10.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Single Choice: DNS Resolution Mode
                        Text(stringResource(R.string.proxy_dns_mode_label), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf(
                                "SYSTEM" to stringResource(R.string.proxy_dns_mode_system),
                                "TRADITIONAL" to stringResource(R.string.proxy_dns_mode_traditional),
                                "DOH" to stringResource(R.string.proxy_dns_mode_doh)
                            ).forEach { (mode, label) ->
                                val isSelected = configState.proxyDnsMode == mode
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) primaryAccent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) primaryAccent else MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    modifier = Modifier.clickable { viewModel.setProxyDnsMode(mode) }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = null,
                                            modifier = Modifier.size(18.dp),
                                            colors = RadioButtonDefaults.colors(
                                                selectedColor = primaryAccent,
                                                unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) primaryAccent else MaterialTheme.colorScheme.onSurface
                                            ),
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }

                        when (configState.proxyDnsMode) {
                            "SYSTEM" -> {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = stringResource(R.string.proxy_dns_system_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                            "TRADITIONAL" -> {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(stringResource(R.string.proxy_dns_server_label), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = configState.proxyDnsServer,
                                    onValueChange = { viewModel.updateProxyDnsServer(it) },
                                    placeholder = { Text(stringResource(R.string.proxy_dns_server_hint), style = MaterialTheme.typography.bodySmall) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    shape = RoundedCornerShape(10.dp),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                    })
                                )

                                Spacer(modifier = Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.expert_quick_fill), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        listOf(
                                            "8.8.8.8" to "8.8.8.8",
                                            "114.114.114.114" to "114.114.114.114",
                                            "1.1.1.1" to "1.1.1.1",
                                            "223.5.5.5" to "223.5.5.5"
                                        ).forEach { (label, dns) ->
                                            val isCurrent = configState.proxyDnsServer.trim() == dns
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (isCurrent) primaryAccent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                                                border = BorderStroke(1.dp, if (isCurrent) primaryAccent else MaterialTheme.colorScheme.outlineVariant),
                                                modifier = Modifier.clickable {
                                                    viewModel.updateProxyDnsServer(dns)
                                                    focusManager.clearFocus()
                                                    keyboardController?.hide()
                                                }
                                            ) {
                                                Text(
                                                    text = label,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        color = if (isCurrent) primaryAccent else MaterialTheme.colorScheme.onSurface,
                                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                                                    ),
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            "DOH" -> {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(stringResource(R.string.proxy_doh_server_label), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = configState.proxyDohServer,
                                    onValueChange = { viewModel.updateProxyDohServer(it) },
                                    placeholder = { Text(stringResource(R.string.proxy_doh_server_hint), style = MaterialTheme.typography.bodySmall) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    shape = RoundedCornerShape(10.dp),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                    })
                                )

                                Spacer(modifier = Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.expert_quick_fill), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        listOf(
                                            "Cloudflare" to "https://cloudflare-dns.com/dns-query",
                                            "Google" to "https://dns.google/dns-query",
                                            "阿里" to "https://dns.alidns.com/dns-query",
                                            "DNSPod" to "https://doh.pub/dns-query"
                                        ).forEach { (label, url) ->
                                            val isCurrent = configState.proxyDohServer.trim() == url
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (isCurrent) primaryAccent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                                                border = BorderStroke(1.dp, if (isCurrent) primaryAccent else MaterialTheme.colorScheme.outlineVariant),
                                                modifier = Modifier.clickable {
                                                    viewModel.updateProxyDohServer(url)
                                                    focusManager.clearFocus()
                                                    keyboardController?.hide()
                                                }
                                            ) {
                                                Text(
                                                    text = label,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        color = if (isCurrent) primaryAccent else MaterialTheme.colorScheme.onSurface,
                                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                                                    ),
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Multiple Choice: Proxy Access Mode
                        Text(stringResource(R.string.expert_proxy_mode_label), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Native Mode Checkbox Chip
                            val isNative = configState.proxyModeNative
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isNative) primaryAccent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    1.dp,
                                    if (isNative) primaryAccent else MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier.clickable { viewModel.setProxyModeNative(!isNative) }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Checkbox(
                                        checked = isNative,
                                        onCheckedChange = null,
                                        modifier = Modifier.size(18.dp),
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = primaryAccent,
                                            uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.proxy_mode_native),
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = if (isNative) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isNative) primaryAccent else MaterialTheme.colorScheme.onSurface
                                        ),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            // WebView Mode Checkbox Chip
                            val isWebview = configState.proxyModeWebview
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isWebview) primaryAccent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    1.dp,
                                    if (isWebview) primaryAccent else MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier.clickable { viewModel.setProxyModeWebview(!isWebview) }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Checkbox(
                                        checked = isWebview,
                                        onCheckedChange = null,
                                        modifier = Modifier.size(18.dp),
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = primaryAccent,
                                            uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.proxy_mode_webview),
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = if (isWebview) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isWebview) primaryAccent else MaterialTheme.colorScheme.onSurface
                                        ),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Probe 7: Speed Test
            OptionRowCard(
                title = stringResource(R.string.opt_speed),
                description = stringResource(R.string.desc_speed),
                icon = Icons.Default.Speed,
                checked = configState.speedEnabled,
                onCheckedChange = { viewModel.setSpeedEnabled(it) },
                accentColor = primaryAccent
            )

            // Speed Test Dedicated Subpanel
            AnimatedVisibility(
                visible = configState.speedEnabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = primaryAccent.copy(alpha = 0.04f),
                    border = BorderStroke(1.dp, primaryAccent.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = stringResource(R.string.speed_url_label),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = primaryAccent)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = configState.speedUrl,
                            onValueChange = { viewModel.updateSpeedUrl(it) },
                            placeholder = { Text(stringResource(R.string.speed_url_hint), style = MaterialTheme.typography.bodySmall) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }
        }
    }

    // 3. Timeout Configuration Card with Presets
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                focusManager.clearFocus()
                keyboardController?.hide()
            },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.timeout_label),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.weight(1f).padding(end = 12.dp)
                )
                OutlinedTextField(
                    value = configState.timeoutMs.toString(),
                    onValueChange = { str ->
                        val num = str.filter { it.isDigit() }.toLongOrNull() ?: 15000L
                        viewModel.updateTimeoutMs(num)
                    },
                    modifier = Modifier.width(120.dp),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center),
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        }
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Timeout Presets
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5000L to "5s", 10000L to "10s", 15000L to "15s", 30000L to "30s").forEach { (ms, label) ->
                    val isSelected = configState.timeoutMs == ms
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.clickable {
                            viewModel.updateTimeoutMs(ms)
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        }
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }

    // 4. Start Diagnosis Action Button
    Button(
        onClick = {
            focusManager.clearFocus()
            keyboardController?.hide()
            viewModel.startLocalDiagnosis(currentLanguage)
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .testTag("start_expert_diagnosis_btn"),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp)
    ) {
        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.start_expert_diagnosis),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
    }
}

/**
 * Full-width, responsive option row with colored icon, title, description, and toggle switch.
 * Ensures text never truncates or cuts off across any screen resolution or language.
 */
@Composable
private fun OptionRowCard(
    title: String,
    description: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (checked) accentColor.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        border = BorderStroke(
            1.dp,
            if (checked) accentColor.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                focusManager.clearFocus()
                keyboardController?.hide()
                onCheckedChange(!checked)
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (checked) accentColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (checked) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.labelSmall.copy(lineHeight = 16.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = accentColor
                )
            )
        }
    }
}

@Composable
private fun ExpertRunningCard(executionState: ExpertExecutionState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                progress = { executionState.progress },
                modifier = Modifier.size(72.dp),
                strokeWidth = 6.dp,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "${(executionState.progress * 100).toInt()}%",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                ),
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = executionState.currentStep,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            LinearProgressIndicator(
                progress = { executionState.progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.expert_running_offline_tip),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ExpertResultsView(
    executionState: ExpertExecutionState,
    onRetest: () -> Unit
) {
    val context = LocalContext.current
    var selectedFilter by remember { mutableStateOf("ALL") }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Success Header
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF10B981).copy(alpha = 0.1f),
            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.local_report_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF065F46)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.expert_results_retained_tip),
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                        color = Color(0xFF047857)
                    )
                }
            }
        }

        // Intelligent Analysis Card
        executionState.analysisReport?.let { report ->
            ExpertAnalysisReportCard(report)
        }

        // Action Buttons Row (Flexible, no text clipping)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onRetest,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.retest_button), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            }

            Button(
                onClick = {
                    val fullLog = buildFullLogString(executionState)
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("NetCheck_Expert_Report", fullLog))
                    Toast.makeText(context, context.getString(R.string.copied_log), Toast.LENGTH_SHORT).show()
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.copy_full_report), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            }
        }

        // Probe Items Breakdown Header
        val results = executionState.results
        val passCount = results.count { it.status == "success" }
        val totalCount = results.size

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.expert_tasks_summary_format, passCount, totalCount),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("ALL" to stringResource(R.string.filter_all), "FAIL" to stringResource(R.string.filter_fail)).forEach { (key, label) ->
                    FilterChip(
                        selected = selectedFilter == key,
                        onClick = { selectedFilter = key },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }

        val filteredResults = results.filter {
            if (selectedFilter == "FAIL") it.status != "success" else true
        }

        if (filteredResults.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (selectedFilter == "FAIL") stringResource(R.string.no_failures_found) else stringResource(R.string.no_results_found),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                filteredResults.forEach { item ->
                    TaskResultItemCard(item)
                }
            }
        }
    }
}

@Composable
private fun ExpertAnalysisReportCard(analysis: AnalysisReport) {
    val statusColor = when (analysis.status) {
        "PASS" -> Color(0xFF10B981)
        "WARN" -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Surface(shape = CircleShape, color = statusColor.copy(alpha = 0.15f), modifier = Modifier.size(32.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = statusColor, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.analysis_card_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(shape = RoundedCornerShape(8.dp), color = statusColor.copy(alpha = 0.15f)) {
                    Text(
                        text = when (analysis.status) {
                            "PASS" -> "PASS"
                            "WARN" -> "WARNING"
                            else -> "CRITICAL"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = statusColor.copy(alpha = 0.08f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = analysis.summary,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium, lineHeight = 20.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(12.dp)
                )
            }

            if (analysis.issues.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.analysis_issue_title), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFFEF4444))
                }
                Spacer(modifier = Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    analysis.issues.forEach { issue ->
                        Text(text = "• $issue", style = MaterialTheme.typography.bodySmall, color = Color(0xFFB91C1C), lineHeight = 18.sp)
                    }
                }
            }

            if (analysis.solutions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.analysis_solution_title), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFF047857))
                }
                Spacer(modifier = Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    analysis.solutions.forEachIndexed { idx, sol ->
                        Text(text = "${idx + 1}. $sol", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskResultItemCard(item: TaskExecutionResult) {
    var expanded by remember { mutableStateOf(false) }
    val isSuccess = item.status == "success"
    val context = LocalContext.current
    val resolvedIp = remember(item.raw_log) { extractLogField(item.raw_log, "Resolved IP: ") }
    val resolutionMethod = remember(item.raw_log) { extractLogField(item.raw_log, "Resolution Method: ") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSuccess) MaterialTheme.colorScheme.surface else Color(0xFFEF4444).copy(alpha = 0.04f)
        ),
        border = BorderStroke(
            1.dp,
            if (isSuccess) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f) else Color(0xFFEF4444).copy(alpha = 0.35f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                    Text(
                        text = item.task,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.task_time_cost, item.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (resolvedIp.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = if (resolutionMethod.isNotEmpty()) {
                                "DNS: $resolvedIp ($resolutionMethod)"
                            } else {
                                "IP: $resolvedIp"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF4F46E5),
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSuccess) Color(0xFF10B981).copy(alpha = 0.12f) else Color(0xFFEF4444).copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = if (isSuccess) stringResource(R.string.status_pass_badge) else stringResource(R.string.status_fail_badge),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isSuccess) Color(0xFF047857) else Color(0xFFDC2626),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF0F172A),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "LOG OUTPUT",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, color = Color(0xFF94A3B8), fontSize = 10.sp)
                                )
                                if (item.raw_log.isNotBlank()) {
                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                            clipboard?.setPrimaryClip(ClipData.newPlainText("Task_Log", item.raw_log))
                                            Toast.makeText(context, context.getString(R.string.copied_log), Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = item.raw_log.ifBlank { stringResource(R.string.no_raw_logs) },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = Color(0xFF34D399),
                                    lineHeight = 16.sp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun buildFullLogString(executionState: ExpertExecutionState): String {
    val sb = StringBuilder()
    sb.append("=========================================\n")
    sb.append("    NetCheck Expert Diagnostics Report   \n")
    sb.append("=========================================\n\n")

    executionState.analysisReport?.let {
        sb.append(it.formattedReport).append("\n\n")
    }

    sb.append("--- Detailed Execution Logs ---\n")
    executionState.results.forEach { res ->
        sb.append("[${res.status.uppercase()}] ${res.task} (${res.durationMs}ms)\n")
        sb.append(res.raw_log).append("\n")
        sb.append("-----------------------------------------\n")
    }
    return sb.toString()
}

private fun extractLogField(log: String, prefix: String): String {
    val idx = log.indexOf(prefix)
    if (idx == -1) return ""
    val start = idx + prefix.length
    val end = log.indexOf('\n', start).let { if (it == -1) log.length else it }
    return log.substring(start, end).trim()
}
