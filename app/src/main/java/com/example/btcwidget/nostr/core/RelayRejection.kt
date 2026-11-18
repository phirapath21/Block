package com.example.btcwidget.nostr.core

/**
 * Why relays turned a post down, from the machine-readable prefix NIP-01 puts on an OK false
 * message ("pow: difficulty 20 required", "rate-limited: slow down"), so the app can say it
 * in the user's language and tell them what to do instead of showing the raw text.
 */
object RelayRejection {
    enum class Reason { DUPLICATE, POW, BLOCKED, RATE_LIMITED, INVALID, RESTRICTED, AUTH, PAYMENT, NO_ANSWER, OTHER }

    data class Explained(val reason: Reason, val detail: String)

    fun explain(message: String?): Explained {
        val m = message?.trim().orEmpty()
        if (m.isEmpty()) return Explained(Reason.NO_ANSWER, "")
        val prefix = m.substringBefore(':', "").trim().lowercase()
        val detail = if (m.contains(':')) m.substringAfter(':').trim() else m
        val reason = when (prefix) {
            "duplicate" -> Reason.DUPLICATE
            "pow" -> Reason.POW
            "blocked" -> Reason.BLOCKED
            "rate-limited" -> Reason.RATE_LIMITED
            "invalid" -> Reason.INVALID
            "restricted" -> Reason.RESTRICTED
            "auth-required" -> Reason.AUTH
            "payment-required" -> Reason.PAYMENT
            else -> Reason.OTHER
        }
        return Explained(reason, detail)
    }

    /**
     * The rejection to tell the user about when none accepted: the one they can most easily do
     * something about (proof of work, waiting, another relay), else the first with any text.
     */
    fun mostUseful(messages: List<String>): Explained {
        val all = messages.filter { it.isNotBlank() }.map { explain(it) }
        val order = listOf(Reason.POW, Reason.RATE_LIMITED, Reason.AUTH, Reason.PAYMENT, Reason.RESTRICTED, Reason.BLOCKED, Reason.INVALID, Reason.OTHER, Reason.DUPLICATE)
        return order.firstNotNullOfOrNull { r -> all.firstOrNull { it.reason == r } } ?: Explained(Reason.NO_ANSWER, "")
    }
}

/** Thrown by publishing when no relay took the event; [why] says why in a form the UI can word. */
class PublishRejected(val why: RelayRejection.Explained) : IllegalStateException(why.detail.ifEmpty { "No relay accepted the post" })
