package com.example.pager

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

private val Mono = FontFamily.Monospace

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xFF2A2D32), Color(0xFF1E2024))), shape)
            .border(1.dp, Color(0xFF3B3F46), shape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, color = Color(0xFF7CFC9A), fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        content()
    }
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontFamily = Mono); Switch(value, onChange)
    }
}

@Composable
fun FuncScreen(onClose: () -> Unit, requestPerm: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(Modifier.fillMaxSize().systemBarsPadding(), color = Color(0xFF101214)) {
            Column {
                Row(Modifier.padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("FUNCTIONS", Modifier.weight(1f), fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("◆ ${PagerCore.gems}", color = Color(0xFF7FE6FF), fontFamily = Mono, fontWeight = FontWeight.Bold,
                        fontSize = 16.sp, modifier = Modifier.padding(end = 12.dp))
                    OutlinedButton(onClick = onClose) { Text("CLOSE") }
                }
                ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
                    listOf("SHOP", "RADIO", "SCREEN", "DEBUG").forEachIndexed { i, t ->
                        Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, fontFamily = Mono) })
                    }
                }
                Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    when (tab) { 0 -> ShopTab(); 1 -> RadioTab(); 2 -> ScreenTab(); else -> DebugTab(requestPerm) }
                }
            }
        }
    }
}

@Composable
private fun ShopTab() {
    Text("Gems: 1 per new day, plus 1 for every message you send or receive.", fontFamily = Mono, fontSize = 12.sp)
    MODELS.forEach { m ->
        val owned = m.id in PagerCore.owned
        val equipped = PagerCore.modelId == m.id
        Section(m.name.uppercase()) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                val ps = RoundedCornerShape(10.dp)
                val wide = m.layout == Layout.WIDE
                Column(
                    Modifier.size(if (wide) 88.dp else 58.dp, if (wide) 58.dp else 86.dp)
                        .background(Brush.verticalGradient(listOf(m.bodyTop, m.bodyBottom)), ps)
                        .border(1.dp, m.edgeLight, ps).padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(Modifier.fillMaxWidth().weight(if (m.layout == Layout.PAPER) 2f else 1f)
                        .background(Brush.verticalGradient(listOf(m.lcdTop, m.lcdBottom)), RoundedCornerShape(3.dp)))
                    if (m.layout != Layout.PAPER)
                        Box(Modifier.fillMaxWidth().weight(1f).background(Brush.verticalGradient(listOf(m.keyTop, m.keyBottom)), RoundedCornerShape(3.dp)))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(m.tagline, fontSize = 12.sp)
                    m.perks.forEach { Text("★ $it", fontSize = 11.sp, color = Color(0xFFFFD166), fontFamily = Mono) }
                }
            }
            when {
                equipped -> OutlinedButton({}, Modifier.fillMaxWidth(), enabled = false) { Text("EQUIPPED") }
                owned -> Button({ PagerCore.equip(m.id) }, Modifier.fillMaxWidth()) { Text("EQUIP") }
                else -> Button({ PagerCore.buy(m) }, Modifier.fillMaxWidth(), enabled = PagerCore.gems >= m.price) { Text("BUY  ◆ ${m.price}") }
            }
        }
    }
}

@Composable
private fun RadioTab() {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.all { it }) Mesh.start(ctx) else PagerCore.log("RADIO PERMS DENIED")
    }
    fun toggle(on: Boolean) {
        if (!on) { Mesh.stop(); return }
        val need = Mesh.permissions().filter { ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED }
        if (need.isEmpty()) Mesh.start(ctx) else launcher.launch(need.toTypedArray())
    }
    Section("MESH RADIO") {
        OutlinedTextField(PagerCore.callsign, {
            PagerCore.callsign = it.uppercase().filter { c -> !c.isWhitespace() && c != '|' }.take(12); PagerCore.saveMeta()
        }, label = { Text("Your callsign") }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !Mesh.on)
        ToggleRow("Radio on", Mesh.on, ::toggle)
        Text("Status: ${Mesh.status}   Relayed: ${Mesh.relayed}", fontFamily = Mono, fontSize = 12.sp)
        Text("Uses Bluetooth/BLE/Wi-Fi via Google Nearby. Needs another phone running this app nearby. Messages and images hop through linked pagers (up to 5 hops). Stop the radio to change your callsign.",
            fontSize = 11.sp, color = Color(0xFF9AA0AA))
    }
    Section("LINKED PAGERS") {
        if (Mesh.peers.isEmpty()) Text("(none)", fontFamily = Mono, fontSize = 12.sp)
        Mesh.peers.values.forEach { Text("◉ $it", fontFamily = Mono, color = Color(0xFFB8E8C0)) }
    }
}

@Composable
private fun ScreenTab() {
    Section("ORIENTATION") {
        listOf("AUTO (follow sensor)" to 0, "PORTRAIT" to 1, "LANDSCAPE" to 2).forEach { (l, v) ->
            if (PagerCore.orient == v) Button({}, Modifier.fillMaxWidth()) { Text("● $l") }
            else OutlinedButton({ PagerCore.orient = v; PagerCore.saveMeta() }, Modifier.fillMaxWidth()) { Text(l) }
        }
        Text("Landscape devices (CM P1) also have a ROTATE button on the body.", fontSize = 11.sp, color = Color(0xFF9AA0AA))
    }
}

@Composable
private fun DebugTab(requestPerm: () -> Unit) {
    var from by remember { mutableStateOf("DISPATCH") }
    var body by remember { mutableStateOf("Test page. Please respond.") }
    var urgent by remember { mutableStateOf(false) }
    val randoms = remember {
        listOf("Server down, call NOC" to "OPS", "Code blue room 4" to "HOSPITAL",
            "Pizza is here" to "FRONT DESK", "Call mom back" to "HOME", "Deploy failed on prod" to "CI-BOT")
    }
    Section("SEND MESSAGE (local)") {
        OutlinedTextField(from, { from = it }, label = { Text("From") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(body, { body = it }, label = { Text("Message") }, modifier = Modifier.fillMaxWidth())
        ToggleRow("Urgent", urgent) { urgent = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ PagerCore.receive(from, body, urgent) }, Modifier.weight(1f)) { Text("SEND NOW") }
            Button({ PagerCore.receiveDelayed(5000, from, body, urgent) }, Modifier.weight(1f)) { Text("IN 5s") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ val r = randoms.random(); PagerCore.receive(r.second, r.first, false) }, Modifier.weight(1f)) { Text("RANDOM") }
            OutlinedButton({ repeat(10) { i -> PagerCore.receive("FLOOD", "Flood message #${i + 1}", i % 4 == 0) } }, Modifier.weight(1f)) { Text("FLOOD x10") }
        }
        Button({ PagerCore.receive("GHOST", "[IMAGE]", false, Sketch.testImage()) }, Modifier.fillMaxWidth()) { Text("SEND TEST IMAGE 128x256") }
    }
    Section("NOTIFICATIONS") {
        Text("Permission: ${if (PagerCore.hasNotifPermission()) "GRANTED" else "NOT GRANTED"}", fontFamily = Mono, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ PagerCore.testNotification(false) }, Modifier.weight(1f)) { Text("TEST") }
            Button({ PagerCore.testNotification(true) }, Modifier.weight(1f)) { Text("TEST URGENT") }
        }
        OutlinedButton(requestPerm, Modifier.fillMaxWidth()) { Text("REQUEST PERMISSION") }
        ToggleRow("System notifications", PagerCore.systemNotify) { PagerCore.systemNotify = it }
        ToggleRow("Beep", PagerCore.beep) { PagerCore.beep = it }
        ToggleRow("Vibrate", PagerCore.vibrate) { PagerCore.vibrate = it }
    }
    Section("MESH TEST (no hardware needed)") {
        ToggleRow("Loopback: sends come back to me", Mesh.loopback) { Mesh.loopback = it }
        Button({ Mesh.inject("GHOST", "ALL", "Injected broadcast", 3) }, Modifier.fillMaxWidth()) { Text("INJECT BROADCAST") }
        Button({ Mesh.inject("GHOST", "ALL", "[IMAGE]", 3, Sketch.testImage()) }, Modifier.fillMaxWidth()) { Text("INJECT IMAGE") }
        Button({ Mesh.inject("GHOST", PagerCore.callsign, "Injected direct msg", 3) }, Modifier.fillMaxWidth()) { Text("INJECT DIRECT TO ME") }
        OutlinedButton({ Mesh.inject("GHOST", "NOBODY", "Relay test", 2) }, Modifier.fillMaxWidth()) { Text("INJECT RELAY-ONLY") }
    }
    Section("GEMS / SHOP") {
        Text("Gems: ${PagerCore.gems}", fontFamily = Mono)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ PagerCore.addGem(1) }, Modifier.weight(1f)) { Text("+1") }
            OutlinedButton({ PagerCore.addGem(10) }, Modifier.weight(1f)) { Text("+10") }
            OutlinedButton({ PagerCore.resetDaily() }, Modifier.weight(1f)) { Text("NEW DAY") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ MODELS.forEach { if (it.id !in PagerCore.owned) PagerCore.owned.add(it.id) }; PagerCore.saveMeta() }, Modifier.weight(1f)) { Text("UNLOCK ALL") }
            OutlinedButton({ PagerCore.owned.clear(); PagerCore.owned.add("cmp1"); PagerCore.equip("cmp1"); PagerCore.gems = 0; PagerCore.saveMeta() }, Modifier.weight(1f)) { Text("RESET") }
        }
    }
    Section("MEMORY") {
        Text("Stored: ${PagerCore.messages.size}  Unread: ${PagerCore.messages.count { !it.read }}", fontFamily = Mono, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ PagerCore.markAllRead() }, Modifier.weight(1f)) { Text("MARK READ") }
            OutlinedButton({ PagerCore.clear() }, Modifier.weight(1f)) { Text("CLEAR ALL") }
        }
    }
    Section("LOG") {
        if (PagerCore.log.isEmpty()) Text("(empty)", fontFamily = Mono, fontSize = 11.sp)
        PagerCore.log.take(40).forEach { Text(it, fontFamily = Mono, fontSize = 11.sp, color = Color(0xFFB8E8C0)) }
    }
}
