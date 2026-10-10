# LinkText

An Android starter app for direct, nearby messaging over Bluetooth Classic. It uses no server, mobile data, Wi-Fi, or internet connection.

## What it does

- Discovers nearby Bluetooth devices and lets a person save one locally as a contact.
- Makes a direct RFCOMM Bluetooth connection for a one-to-one conversation.
- Sends plain text and Unicode emoji.
- Stores messages and contact labels only on the device.
- Queues an outgoing message locally when a contact is out of Bluetooth range, then sends it when that same contact reconnects.
- Includes an opt-in, one-hop relay beta: a third paired phone can temporarily carry a message from a sender to its intended recipient.
- Supports dark theme and quoted replies (tap a message to reply to it).

## Important product boundary

This is **nearby** messaging: both phones need Bluetooth enabled and be within Bluetooth range for instant delivery. Bluetooth Classic normally reaches about 10 metres, sometimes farther depending on hardware and obstructions. If a recipient is unavailable, LinkText keeps the message queued until those same two phones reconnect; it is not a long-distance messenger and does not relay through other phones.

## Relay beta: Phone 1 → Phone 2 → Phone 3

This version supports a deliberate, one-hop relay. Pair Phone 1 with Phone 2, and Phone 2 with Phone 3. Add the intended recipient to Phone 1's contacts. On Phone 2, turn on **Relay mode**. Phone 1 connects to Phone 2, opens Phone 3's chat, and sends; the message says **Handed to relay**. Phone 2 then connects to Phone 3 and sends the queued relay message automatically.

Relay mode is deliberately opt-in. This beta uses Bluetooth's encrypted link on each hop, but it does **not** yet provide end-to-end encryption: the relay phone can read packets it carries. Do not use it for sensitive messages. Multi-hop routing, delivery receipts, expiry rules, and end-to-end encryption are required before treating this as a private or reliable mesh messenger.

## Open in Android Studio

1. Open this folder in the latest stable Android Studio.
2. Use a physical Android device (two are needed to test a real chat).
3. Build and run. Android 12+ will ask for Nearby devices permission; older Android versions require location permission for discovery.
4. Turn Bluetooth on, use **Nearby** to discover a device, select it, then open a conversation and tap **Connect**.

Pair the phones in the Android Bluetooth settings first for the smoothest connection. The app does not need an account, phone number, or internet access.

## Product recommendations before release

- Add app-level end-to-end encryption with verified identity keys; Bluetooth pairing alone should not be the privacy model.
- Add message delivery/read indicators and an explicit reconnect state.
- Give contacts a QR-code exchange flow so people do not need to choose ambiguous device names.
- Consider an optional store-and-forward mesh only after the direct-chat experience is solid; it adds substantial battery, privacy, conflict-resolution, and delivery complexity.

