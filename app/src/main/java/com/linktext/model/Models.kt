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
    val replyPreview: String? = null,
    val delivery: DeliveryStatus = DeliveryStatus.SENT
)

enum class DeliveryStatus { QUEUED, SENT, HANDED_TO_RELAY }

/** A one-hop, store-and-forward packet held only on a user-opted-in relay phone. */
data class RelayEnvelope(
    val id: String,
    val senderAddress: String,
    val recipientAddress: String,
    val body: String,
    val timestamp: Long,
    val replyToId: String? = null,
    val replyPreview: String? = null
)

data class NearbyDevice(val address: String, val name: String, val isPaired: Boolean = false)

