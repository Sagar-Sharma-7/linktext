package com.linktext

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.linktext.bluetooth.BluetoothChatManager
import com.linktext.data.LocalStore
import com.linktext.model.ChatMessage
import com.linktext.model.Contact
import com.linktext.model.DeliveryStatus
import com.linktext.model.NearbyDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

class MainActivity : ComponentActivity() {
    private val viewModel: LinkTextViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LinkTextApp(viewModel, ::hasBluetoothPermission, ::askToEnableBluetooth) }
    }

    private fun hasBluetoothPermission(): Boolean = requiredBluetoothPermissions().all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun askToEnableBluetooth() {
        startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
    }
}

private fun requiredBluetoothPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
} else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

class LinkTextViewModel(application: android.app.Application) : AndroidViewModel(application) {
    private val store = LocalStore(application)
    private val bluetooth = BluetoothChatManager(application)
    private val _contacts = MutableStateFlow(store.contacts())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()
    private val _messages = MutableStateFlow(store.messages())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    val nearby = bluetooth.nearby
    val status = bluetooth.status
    val connectionAddress = bluetooth.connectionAddress

    init {
        viewModelScope.launch {
            bluetooth.incoming.collect { payload -> payload?.let(::receive) }
        }
        viewModelScope.launch {
            bluetooth.connectionAddress.collect { address -> address?.let(::flushQueuedMessages) }
        }
    }

    fun begin() { bluetooth.startListening() }
    fun discover() = bluetooth.discover()
    fun bluetoothAvailable() = bluetooth.isAvailable()
    fun bluetoothEnabled() = bluetooth.isEnabled()
    fun connect(contact: Contact) = bluetooth.connect(contact.address)

    fun saveDevice(device: NearbyDevice) {
        store.saveContact(Contact(device.address, device.name))
        _contacts.value = store.contacts()
    }

    fun send(contact: Contact, text: String, reply: ChatMessage?): Boolean {
        if (text.isBlank()) return false
        val initial = ChatMessage(UUID.randomUUID().toString(), contact.address, text.trim(), true, System.currentTimeMillis(), reply?.id, reply?.body)
        val sent = bluetooth.connectionAddress.value == contact.address && bluetooth.send(messagePayload(initial))
        store.saveMessage(initial.copy(delivery = if (sent) DeliveryStatus.SENT else DeliveryStatus.QUEUED))
        _messages.value = store.messages()
        return true
    }

    private fun flushQueuedMessages(address: String) {
        _messages.value
            .filter { it.sentByMe && it.contactAddress == address && it.delivery == DeliveryStatus.QUEUED }
            .forEach { message ->
                if (bluetooth.send(messagePayload(message))) {
                    store.updateDelivery(message.id, DeliveryStatus.SENT)
                }
            }
        _messages.value = store.messages()
    }

    private fun messagePayload(message: ChatMessage) = JSONObject().apply {
        put("id", message.id); put("body", message.body); put("timestamp", message.timestamp)
        put("replyToId", message.replyToId ?: ""); put("replyPreview", message.replyPreview ?: "")
    }

    private fun receive(payload: JSONObject) {
        val address = connectionAddress.value ?: return
        val message = ChatMessage(
            payload.optString("id", UUID.randomUUID().toString()), address, payload.optString("body"), false,
            payload.optLong("timestamp", System.currentTimeMillis()), payload.optString("replyToId").ifBlank { null },
            payload.optString("replyPreview").ifBlank { null }, DeliveryStatus.SENT
        )
        if (_messages.value.none { it.id == message.id }) {
            store.saveMessage(message); _messages.value = store.messages()
        }
    }

    override fun onCleared() { bluetooth.close() }
}

@Composable
private fun LinkTextApp(viewModel: LinkTextViewModel, hasPermission: () -> Boolean, enableBluetooth: () -> Unit) {
    val contacts by viewModel.contacts.collectAsState()
    val nearby by viewModel.nearby.collectAsState()
    val status by viewModel.status.collectAsState()
    var selected by remember { mutableStateOf<Contact?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.all { it }) viewModel.begin()
    }

    MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme()) {
        Surface(Modifier.fillMaxSize()) {
            if (selected == null) HomeScreen(
                contacts, nearby, status,
                onPrepare = {
                    if (!hasPermission()) permissionLauncher.launch(requiredBluetoothPermissions())
                    else if (!viewModel.bluetoothEnabled()) enableBluetooth()
                    else { viewModel.begin(); viewModel.discover() }
                },
                onSave = viewModel::saveDevice,
                onOpen = { selected = it }
            ) else ChatScreen(selected!!, viewModel, { selected = null })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    contacts: List<Contact>, nearby: List<NearbyDevice>, status: String,
    onPrepare: () -> Unit, onSave: (NearbyDevice) -> Unit, onOpen: (Contact) -> Unit
) {
    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("LinkText", fontWeight = FontWeight.Bold) }) },
        floatingActionButton = { FloatingActionButton(onClick = onPrepare) { Icon(Icons.Default.Bluetooth, "Find nearby people") } }
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                    Text(status, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("Direct, private nearby chats. No account, SIM, or data connection needed.", style = MaterialTheme.typography.bodySmall)
                } }
            }
            item { Text("Your contacts", style = MaterialTheme.typography.titleMedium) }
            if (contacts.isEmpty()) item { Text("Use the Bluetooth button to add someone nearby.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(contacts, key = { it.address }) { contact -> ContactCard(contact, { onOpen(contact) }) }
            if (nearby.isNotEmpty()) {
                item { Text("Nearby devices", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp)) }
                items(nearby, key = { it.address }) { device ->
                    Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(device.name); Text(device.address, style = MaterialTheme.typography.labelSmall) }
                        TextButton(onClick = { onSave(device) }) {
                            Icon(Icons.Default.Add, null)
                            Spacer(Modifier.width(4.dp))
                            Text(if (device.isPaired) "Add paired" else "Add")
                        }
                    } }
                }
            }
        }
    }
}

@Composable
private fun ContactCard(contact: Contact, open: () -> Unit) = Card(Modifier.fillMaxWidth()) {
    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(contact.name, style = MaterialTheme.typography.titleMedium); Text("Available when nearby", style = MaterialTheme.typography.bodySmall) }
        TextButton(onClick = open) { Text("Chat") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreen(contact: Contact, viewModel: LinkTextViewModel, back: () -> Unit) {
    val messages by viewModel.messages.collectAsState()
    val connectedAddress by viewModel.connectionAddress.collectAsState()
    val conversation = messages.filter { it.contactAddress == contact.address }
    var text by remember { mutableStateOf("") }
    var reply by remember { mutableStateOf<ChatMessage?>(null) }
    val listState = rememberLazyListState()
    LaunchedEffect(conversation.size) { if (conversation.isNotEmpty()) listState.animateScrollToItem(conversation.lastIndex) }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(
            title = { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(contact.name); Text(if (connectedAddress == contact.address) "Connected" else "Nearby required", style = MaterialTheme.typography.labelSmall) } },
            navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = { TextButton(onClick = { viewModel.connect(contact) }) { Text("Connect") } }
        ) }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding()) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(conversation, key = { it.id }) { message -> MessageBubble(message, { reply = message }) }
            }
            if (reply != null) ReplyBar(reply!!, { reply = null })
            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.weight(1f), placeholder = { Text("Message — emoji work too") }, maxLines = 4)
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { if (viewModel.send(contact, text, reply)) { text = ""; reply = null } }) { Icon(Icons.Default.Send, "Send") }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(message: ChatMessage, reply: () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = if (message.sentByMe) Alignment.CenterEnd else Alignment.CenterStart) {
        Card(Modifier.combinedClickable(onClick = reply, onLongClick = reply)) {
            Column(Modifier.padding(12.dp).width(260.dp)) {
                message.replyPreview?.let { Text("↪ $it", style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                SelectionContainer { Text(message.body) }
                if (message.sentByMe && message.delivery == DeliveryStatus.QUEUED) {
                    Text("Queued — deliver when nearby", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ReplyBar(reply: ChatMessage, dismiss: () -> Unit) = Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
    Text("Replying to: ${reply.body}", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
    TextButton(onClick = dismiss) { Text("Cancel") }
}
