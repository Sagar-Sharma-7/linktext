package com.linktext.data

import android.content.Context
import com.linktext.model.ChatMessage
import com.linktext.model.Contact
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
            replyPreview = json.optString("replyPreview").ifBlank { null }
        )
    }

    fun saveMessage(message: ChatMessage) {
        val all = messages() + message
        // This deliberately keeps the starter lightweight. Move to Room for a production message archive.
        writeArray("messages", all.takeLast(1_000).map { JSONObject().apply {
            put("id", it.id); put("contactAddress", it.contactAddress); put("body", it.body)
            put("sentByMe", it.sentByMe); put("timestamp", it.timestamp)
            put("replyToId", it.replyToId ?: ""); put("replyPreview", it.replyPreview ?: "")
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

