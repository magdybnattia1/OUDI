package com.oudi.smarttv

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { OudiScreen() }
    }
}

@Composable
private fun OudiScreen() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val discoveryManager = remember(context) { TvDiscoveryManager(context) }

    var heardText by remember { mutableStateOf("اضغط على الميكروفون وقل أمراً للتلفزيون") }
    var response by remember { mutableStateOf("أنا OUDI، جاهز أسمعك 🎙️") }
    var pendingListen by remember { mutableStateOf(false) }
    var discoveryStatus by remember { mutableStateOf("لم يبدأ البحث عن التلفزيون بعد.") }
    var discoveredTvs by remember { mutableStateOf<List<TvDevice>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var manualIp by remember { mutableStateOf("") }
    var manualIpError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(discoveryManager) {
        onDispose { discoveryManager.stopDiscovery() }
    }

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull().orEmpty()
        if (spoken.isNotBlank()) {
            heardText = spoken
            response = CommandInterpreter.describe(spoken)
        } else {
            response = "لم أسمع أمراً واضحاً. جرّب مرة أخرى."
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) pendingListen = true
        else response = "لازم تسمح باستخدام الميكروفون عشان OUDI يسمعك."
    }

    LaunchedEffect(pendingListen) {
        if (pendingListen) {
            pendingListen = false
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-EG")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "قول أمر للتلفزيون")
            }
            try {
                speechLauncher.launch(intent)
            } catch (_: Exception) {
                response = "خدمة التعرف على الصوت غير متاحة على الهاتف."
            }
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Color(0xFF67E8F9),
        background = Color(0xFF07111F),
        surface = Color(0xFF122238)
    )) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF07111F)) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)
            ) {
                Text("OUDI", style = MaterialTheme.typography.displayLarge, color = Color(0xFF67E8F9))
                Text("مساعد التلفزيون الذكي", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text("تكلّم… وسيقوم OUDI بفهم أمرك", color = Color(0xFFB8C7D9))

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF122238)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("سمعت منك:", color = Color(0xFF67E8F9))
                        Text(heardText, color = Color.White)
                        HorizontalDivider(color = Color(0xFF31445C))
                        Text("رد OUDI:", color = Color(0xFF67E8F9))
                        Text(response, color = Color.White)
                    }
                }

                Button(
                    onClick = {
                        val granted = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) pendingListen = true
                        else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(Icons.Filled.Mic, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text("اتكلم مع OUDI")
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF122238)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("الاتصال بالتلفزيون", color = Color(0xFF67E8F9), style = MaterialTheme.typography.titleMedium)
                        Text(discoveryStatus, color = Color.White)

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    discoveredTvs = discoveredTvs.filter { it.isManual }
                                    isSearching = true
                                    discoveryManager.startDiscovery(
                                        onDevicesChanged = { devices ->
                                            val manualDevices = discoveredTvs.filter { it.isManual }
                                            discoveredTvs = devices + manualDevices.filterNot { manual ->
                                                devices.any { found -> found.host == manual.host }
                                            }
                                        },
                                        onStatusChanged = {
                                            discoveryStatus = it
                                            if (it.startsWith("فشل") || it.startsWith("تعذر") || it.startsWith("انتهى")) {
                                                isSearching = false
                                            }
                                        }
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                enabled = !isSearching
                            ) { Text("اكتشف التلفزيون") }

                            OutlinedButton(
                                onClick = {
                                    discoveryManager.stopDiscovery()
                                    isSearching = false
                                    discoveryStatus = "تم إيقاف البحث."
                                },
                                modifier = Modifier.weight(1f),
                                enabled = isSearching
                            ) { Text("إيقاف البحث") }
                        }

                        OutlinedTextField(
                            value = manualIp,
                            onValueChange = { value ->
                                manualIp = value.filter { it.isDigit() || it == '.' }.take(15)
                                manualIpError = null
                            },
                            label = { Text("عنوان IP يدوي (اختياري)") },
                            placeholder = { Text("192.168.1.25") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                val address = manualIp.trim()
                                if (!isValidIpv4Address(address)) {
                                    manualIpError = "اكتب عنوان IPv4 صحيحًا، مثل 192.168.1.25"
                                } else if (discoveredTvs.any { it.host == address && !it.isManual }) {
                                    manualIpError = "العنوان ده ظهر بالفعل في نتائج البحث."
                                } else {
                                    discoveredTvs = discoveredTvs.filterNot { it.host == address && it.isManual } +
                                        TvDevice(
                                            name = "عنوان مضاف يدويًا",
                                            host = address,
                                            port = 6467,
                                            isManual = true
                                        )
                                    manualIp = ""
                                    manualIpError = null
                                    discoveryStatus = "تمت إضافة العنوان يدويًا. لم يتم اختبار الاتصال أو الاقتران بعد."
                                }
                            },
                            enabled = manualIp.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("إضافة عنوان يدوي")
                        }
                        manualIpError?.let {
                            Text(it, color = Color(0xFFFF6B6B), style = MaterialTheme.typography.bodySmall)
                        }

                        if (discoveredTvs.isEmpty()) {
                            Text(
                                "لم يتم العثور على جهاز حتى الآن.",
                                color = Color(0xFFB8C7D9),
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            discoveredTvs.forEach { tv ->
                                HorizontalDivider(color = Color(0xFF31445C))
                                Text(tv.name, color = Color.White, style = MaterialTheme.typography.titleSmall)
                                Text("${tv.host}:${tv.port}", color = Color(0xFFB8C7D9))
                                Text(
                                    if (tv.isManual) {
                                        "عنوان مضاف يدويًا؛ لم يتم التحقق من وجود الخدمة أو بدء الاقتران."
                                    } else {
                                        "تم اكتشاف الخدمة فقط؛ الاقتران والتحكم لم يُفعّلا بعد."
                                    },
                                    color = Color(0xFFFFD166),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                Text(
                    "نسخة تجريبية 0.2.2 — اكتشاف الشبكة مع إدخال IP يدوي، والاقتران قيد التطوير",
                    color = Color(0xFF8294AA),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}


private fun isValidIpv4Address(value: String): Boolean {
    val parts = value.trim().split(".")
    if (parts.size != 4) return false

    return parts.all { part ->
        if (part.isEmpty() || part.length > 3 || (part.length > 1 && part.startsWith("0"))) {
            false
        } else {
            part.toIntOrNull()?.let { it in 0..255 } ?: false
        }
    }
}
