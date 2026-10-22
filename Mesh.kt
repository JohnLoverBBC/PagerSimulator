package com.example.pager

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import org.json.JSONObject
import java.util.UUID

/** Flood-routed mesh over Nearby Connections (BLE + Bluetooth + Wi-Fi). Carries text and 128x256 images. */
object Mesh {
    private const val SERVICE = "com.example.pager.mesh"
    private var client: ConnectionsClient? = null
    val peers = mutableStateMapOf<String, String>()      // endpointId -> callsign
    private val pending = HashMap<String, String>()
    private val seen = LinkedHashSet<String>()
    var on by mutableStateOf(false)
    var status by mutableStateOf("RADIO OFF")
    var relayed by mutableIntStateOf(0)
    var loopback by mutableStateOf(false)               // debug: deliver sends to yourself when no peers

    fun permissions(): List<String> = when {
        Build.VERSION.SDK_INT >= 33 -> listOf(
            Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.NEARBY_WIFI_DEVICES)
        Build.VERSION.SDK_INT >= 31 -> listOf(
            Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.ACCESS_FINE_LOCATION)
        else -> listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun myName() = "${PagerCore.callsign}|${PagerCore.uid}"

    fun start(ctx: Context) {
        if (on) return
        val c = Nearby.getConnectionsClient(ctx.applicationContext)
        client = c
        c.startAdvertising(myName(), SERVICE, lifecycle,
            AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build())
            .addOnSuccessListener { PagerCore.log("MESH advertising") }
            .addOnFailureListener { PagerCore.log("ADV FAIL ${it.message}"); status = "ADV FAIL" }
        c.startDiscovery(SERVICE, discovery,
            DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build())
            .addOnSuccessListener { PagerCore.log("MESH scanning") }
            .addOnFailureListener { PagerCore.log("SCAN FAIL ${it.message}"); status = "SCAN FAIL" }
        on = true; status = "SCANNING"
    }

    fun stop() {
        client?.apply { stopAdvertising(); stopDiscovery(); stopAllEndpoints() }
        peers.clear(); pending.clear(); on = false; status = "RADIO OFF"
        PagerCore.log("RADIO OFF")
    }

    private val discovery = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(id: String, info: DiscoveredEndpointInfo) {
            val theirUid = info.endpointName.substringAfter('|', "")
            if (PagerCore.uid < theirUid && !peers.containsKey(id)) {   // lower uid initiates
                client?.requestConnection(myName(), id, lifecycle)
                    ?.addOnFailureListener { PagerCore.log("CONN REQ FAIL ${it.message}") }
            }
        }
        override fun onEndpointLost(id: String) {}
    }

    private val lifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(id: String, info: ConnectionInfo) {
            pending[id] = info.endpointName.substringBefore('|')
            client?.acceptConnection(id, payloads)
        }
        override fun onConnectionResult(id: String, r: ConnectionResolution) {
            if (r.status.isSuccess) {
                peers[id] = pending[id] ?: "?"
                status = "LINKED ${peers.size}"
                PagerCore.log("LINK UP: ${peers[id]}"); PagerCore.flash("LINK: ${peers[id]}")
            } else PagerCore.log("LINK FAIL ${r.status.statusMessage}")
        }
        override fun onDisconnected(id: String) {
            PagerCore.log("LINK DOWN: ${peers[id]}")
            peers.remove(id)
            status = if (peers.isEmpty()) "SCANNING" else "LINKED ${peers.size}"
        }
    }

    private val payloads = object : PayloadCallback() {
        override fun onPayloadReceived(id: String, p: Payload) { p.asBytes()?.let { handle(String(it), id) } }
        override fun onPayloadTransferUpdate(id: String, u: PayloadTransferUpdate) {}
    }

    fun handle(json: String, from: String?) {
        try {
            val o = JSONObject(json)
            val id = o.getString("id")
            if (!seen.add(id)) return
            while (seen.size > 500) seen.remove(seen.first())
            val to = o.getString("to"); val ttl = o.getInt("ttl")
            val mine = to == "ALL" || to.equals(PagerCore.callsign, true)
            if (mine) PagerCore.receive(o.getString("from"), o.getString("body"), o.optBoolean("urgent"),
                o.optString("img", "").ifEmpty { null })
            if ((to == "ALL" || !mine) && ttl > 0) {
                o.put("ttl", ttl - 1)
                val targets = peers.keys.filter { it != from }
                if (targets.isNotEmpty()) {
                    client?.sendPayload(targets, Payload.fromBytes(o.toString().toByteArray()))
                    relayed++; PagerCore.log("RELAY ${o.getString("from")}>$to ttl=${ttl - 1} x${targets.size}")
                }
            }
        } catch (e: Exception) { PagerCore.log("BAD PACKET ${e.message}") }
    }

    /** Returns a status string starting with SENT on success. Each successful send earns a gem. */
    fun send(to: String, body: String, urgent: Boolean = false, img: String? = null): String {
        if (loopback && (!on || peers.isEmpty())) {
            PagerCore.receive(PagerCore.callsign, body, urgent, img)
            PagerCore.addGem()
            return "SENT>LOOP  +1 GEM"
        }
        if (!on) return "RADIO OFF"
        if (peers.isEmpty()) return "NO PEERS"
        val o = JSONObject().put("id", UUID.randomUUID().toString()).put("from", PagerCore.callsign)
            .put("to", to).put("body", body).put("ttl", 5).put("urgent", urgent)
        if (img != null) o.put("img", img)
        seen.add(o.getString("id"))
        client?.sendPayload(peers.keys.toList(), Payload.fromBytes(o.toString().toByteArray()))
        PagerCore.addGem()
        PagerCore.log("TX >$to: $body${if (img != null) " [IMG]" else ""}  +1 gem")
        return "SENT>$to  +1 GEM"
    }

    /** Debug: fake an incoming mesh packet without any hardware. */
    fun inject(from: String, to: String, body: String, ttl: Int, img: String? = null) {
        val o = JSONObject().put("id", UUID.randomUUID().toString()).put("from", from)
            .put("to", to).put("body", body).put("ttl", ttl)
        if (img != null) o.put("img", img)
        handle(o.toString(), null)
    }
}
