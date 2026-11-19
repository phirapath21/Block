package com.example.btcwidget.nostr.core

/**
 * Who a wallet payment was a tip with. A zap's invoice carries the signed zap request
 * (kind 9734) as its description, so a wallet's history can say "Tip from Dev" instead of
 * "Received": the request's author tipped (incoming), its "p" is who was tipped (outgoing).
 */
data class WalletTip(val person: String, val comment: String) {
    companion object {
        /** The tip in [description], or null when the payment isn't a zap (or the request isn't signed right). */
        fun of(description: String, incoming: Boolean): WalletTip? {
            val text = description.trim()
            if (!text.startsWith("{")) return null
            val request = runCatching { Event.fromJson(text) }.getOrNull() ?: return null
            if (request.kind != Kind.ZAP_REQUEST || !request.verify()) return null
            val person = if (incoming) request.pubkey else request.firstTag("p")?.getOrNull(1) ?: return null
            if (!Hex.isKey(person)) return null
            return WalletTip(person, request.content.trim())
        }
    }
}
