package com.linktext.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.linktext.model.NearbyDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.UUID

/** One active RFCOMM stream. Discovery and connection permission must be granted by the caller. */
@SuppressLint("MissingPermission")
class BluetoothChatManager(private val context: Context) {
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val serviceUuid = UUID.fromString("b3e7e4bd-0dcb-4cd1-8c4d-5a3651c9d4b8")
    private var serverSocket: BluetoothServerSocket? = null
    private var socket: BluetoothSocket? = null
    private var writer: BufferedWriter? = null
    private var readerJob: Job? = null
    private var receiverRegistered = false

    private val _nearby = MutableStateFlow<List<NearbyDevice>>(emptyList())
    val nearby: StateFlow<List<NearbyDevice>> = _nearby.asStateFlow()
    private val _connectionAddress = MutableStateFlow<String?>(null)
    val connectionAddress: StateFlow<String?> = _connectionAddress.asStateFlow()
    private val _incoming = MutableStateFlow<JSONObject?>(null)
    val incoming: StateFlow<JSONObject?> = _incoming.asStateFlow()
    private val _status = MutableStateFlow("Bluetooth is off")
    val status: StateFlow<String> = _status.asStateFlow()

    private val discoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
                    val name = device.name ?: "Unnamed device"
                    _nearby.value = (_nearby.value + NearbyDevice(device.address, name)).distinctBy { it.address }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> _status.value = "Nearby scan finished"
            }
        }
    }

    fun isAvailable(): Boolean = adapter != null
    fun isEnabled(): Boolean = adapter?.isEnabled == true

    fun discover() {
        val localAdapter = adapter ?: run { _status.value = "Bluetooth is not available on this phone"; return }
        if (!localAdapter.isEnabled) { _status.value = "Turn Bluetooth on to find people nearby"; return }
        _nearby.value = emptyList()
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(context, discoveryReceiver, IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }, ContextCompat.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
        }
        localAdapter.cancelDiscovery()
        _status.value = "Scanning for nearby devices…"
        localAdapter.startDiscovery()
    }

    fun startListening() = scope.launch {
        val localAdapter = adapter ?: return@launch
        runCatching {
            serverSocket?.close()
            serverSocket = localAdapter.listenUsingRfcommWithServiceRecord("LinkText", serviceUuid)
            while (true) attach(serverSocket?.accept() ?: break)
        }.onFailure { _status.value = "Unable to accept a Bluetooth connection" }
    }

    fun connect(address: String) = scope.launch {
        val localAdapter = adapter ?: return@launch
        runCatching {
            _status.value = "Connecting…"
            localAdapter.cancelDiscovery()
            val device = localAdapter.getRemoteDevice(address)
            val newSocket = device.createRfcommSocketToServiceRecord(serviceUuid)
            newSocket.connect()
            attach(newSocket)
        }.onFailure { _status.value = "Could not connect. Pair both phones first, then try again." }
    }

    fun send(payload: JSONObject): Boolean = runCatching {
        synchronized(this) {
            val currentWriter = writer ?: error("No connection")
            currentWriter.write(payload.toString())
            currentWriter.newLine()
            currentWriter.flush()
        }
        true
    }.getOrElse { _status.value = "Message not sent: device is not connected"; false }

    private fun attach(newSocket: BluetoothSocket) {
        socket?.close()
        socket = newSocket
        writer = BufferedWriter(OutputStreamWriter(newSocket.outputStream, Charsets.UTF_8))
        _connectionAddress.value = newSocket.remoteDevice.address
        _status.value = "Connected to ${newSocket.remoteDevice.name ?: "nearby device"}"
        readerJob?.cancel()
        readerJob = scope.launch {
            runCatching {
                BufferedReader(InputStreamReader(newSocket.inputStream, Charsets.UTF_8)).useLines { lines ->
                    lines.forEach { line -> _incoming.value = JSONObject(line) }
                }
            }
            _connectionAddress.value = null
            _status.value = "Connection ended"
        }
    }

    fun close() {
        runCatching { if (receiverRegistered) context.unregisterReceiver(discoveryReceiver) }
        receiverRegistered = false
        readerJob?.cancel(); runCatching { socket?.close() }; writer = null; runCatching { serverSocket?.close() }
    }
}

