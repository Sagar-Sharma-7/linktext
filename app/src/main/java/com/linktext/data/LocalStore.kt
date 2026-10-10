package com.linktext.data

import android.content.Context
import com.linktext.model.ChatMessage
import com.linktext.model.Contact
import com.linktext.model.DeliveryStatus
import com.linktext.model.RelayEnvelope
import org.json.JSONArray
import org.json.JSONObject

/** Local-only storage. Nothing is uploaded or shared outside the Bluetooth connection. */
class LocalStore(context: Context) {
    private val preferences = context.getSharedPreferences("link_text_local", Context.MODE_PRIVATE)

    fun contacts(): List<Contact> = readArray("contacts").map { json ->
        Contact(json.getString("address"), json.getString("name"), json.optLong("savedAt"))
    }.sortedBy { it.name.lowercase() }

    fun saveContact(contact: Contact) {
        val all = contacts().filterNot { it.address == contact.address } + contact
        writeArray("contacts", all.map { JSONObject().apply {
            put("address", it.address); put("name", it.name); put("savedAt", it.savedAt)
        } })
    }

    fun messages(): List<ChatMessage> = readArray("messages").map { json ->
        ChatMessage(
            id = json.getString("id"), contactAddress = json.getString("contactAddress"),
            body = json.getString("body"), sentByMe = json.getBoolean("sentByMe"),
            timestamp = json.getLong("timestamp"), replyToId = json.optString("replyToId").ifBlank { null },
            replyPreview = json.optString("replyPreview").ifBlank { null },
            delivery = runCatching { DeliveryStatus.valueOf(json.optString("delivery", DeliveryStatus.SENT.name)) }
                .getOrDefault(DeliveryStatus.SENT)
        )
    }

    fun saveMessage(message: ChatMessage) {
        writeMessages(messages() + message)
    }

    fun updateDelivery(id: String, delivery: DeliveryStatus) {
        writeMessages(messages().map { if (it.id == id) it.copy(delivery = delivery) else it })
    }

    fun relayEnabled(): Boolean = preferences.getBoolean("relay_enabled", false)

    fun setRelayEnabled(enabled: Boolean) {
        preferences.edit().putBoolean("relay_enabled", enabled).apply()
    }

    fun relayQueue(): List<RelayEnvelope> = readArray("relay_queue").map { json ->
        RelayEnvelope(
            id = json.getString("id"), senderAddress = json.getString("senderAddress"),
            recipientAddress = json.getString("recipientAddress"), body = json.getString("body"),
            timestamp = json.getLong("timestamp"), replyToId = json.optString("replyToId").ifBlank { null },
            replyPreview = json.optString("replyPreview").ifBlank { null }
        )
    }

    fun saveRelay(envelope: RelayEnvelope) {
        val existing = relayQueue()
        if (existing.any { it.id == envelope.id }) return
        writeArray("relay_queue", (existing + envelope).takeLast(100).map { JSONObject().apply {
            put("id", it.id); put("senderAddress", it.senderAddress); put("recipientAddress", it.recipientAddress)
            put("body", it.body); put("timestamp", it.timestamp)
            put("replyToId", it.replyToId ?: ""); put("replyPreview", it.replyPreview ?: "")
        } })
    }

    fun removeRelay(id: String) {
        writeArray("relay_queue", relayQueue().filterNot { it.id == id }.map { JSONObject().apply {
            put("id", it.id); put("senderAddress", it.senderAddress); put("recipientAddress", it.recipientAddress)
            put("body", it.body); put("timestamp", it.timestamp)
            put("replyToId", it.replyToId ?: ""); put("replyPreview", it.replyPreview ?: "")
        } })
    }

    private fun writeMessages(all: List<ChatMessage>) {
        // This deliberately keeps the starter lightweight. Move to Room for a production message archive.
        writeArray("messages", all.takeLast(1_000).map { JSONObject().apply {
            put("id", it.id); put("contactAddress", it.contactAddress); put("body", it.body)
            put("sentByMe", it.sentByMe); put("timestamp", it.timestamp)
            put("replyToId", it.replyToId ?: ""); put("replyPreview", it.replyPreview ?: "")
            put("delivery", it.delivery.name)
        } })
    }

    private fun readArray(key: String): List<JSONObject> = runCatching {
        val array = JSONArray(preferences.getString(key, "[]"))
        List(array.length()) { array.getJSONObject(it) }
    }.getOrDefault(emptyList())

    private fun writeArray(key: String, values: List<JSONObject>) {
        preferences.edit().putString(key, JSONArray(values).toString()).apply()
    }
}

