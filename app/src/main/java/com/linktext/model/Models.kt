package com.linktext.model

data class Contact(
    val address: String,
    val name: String,
    val savedAt: Long = System.currentTimeMillis()
)

data class ChatMessage(
    val id: String,
    val contactAddress: String,
    val body: String,
    val sentByMe: Boolean,
    val timestamp: Long,
    val replyToId: String? = null,
    val replyPreview: String? = null
)

data class NearbyDevice(val address: String, val name: String)

