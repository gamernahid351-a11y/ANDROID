package com.primex.gateway.parser

import com.primex.gateway.model.ParsedSmsResult
import com.primex.gateway.model.PaymentMethod

object SmsParser {

    private val DISALLOWED_PATTERNS = listOf(
        Regex("otp", RegexOption.IGNORE_CASE),
        Regex("\\bpin\\b", RegexOption.IGNORE_CASE),
        Regex("verification\\s*code", RegexOption.IGNORE_CASE),
        Regex("security\\s*code", RegexOption.IGNORE_CASE),
        Regex("password", RegexOption.IGNORE_CASE),
        Regex("cash\\s*out", RegexOption.IGNORE_CASE),
        Regex("send\\s*money\\s*to", RegexOption.IGNORE_CASE),
        Regex("payment\\s*to", RegexOption.IGNORE_CASE),
        Regex("mobile\\s*recharge", RegexOption.IGNORE_CASE),
        Regex("failed", RegexOption.IGNORE_CASE),
        Regex("declined", RegexOption.IGNORE_CASE),
        Regex("unsuccessful", RegexOption.IGNORE_CASE),
    )

    private fun cleanPhoneNumber(raw: String): String {
        val digits = raw.replace(Regex("\\D"), "")
        return when {
            digits.startsWith("8801") && digits.length == 13 -> digits.substring(2)
            digits.startsWith("01") && digits.length == 11 -> digits
            else -> raw.trim()
        }
    }

    private fun cleanAmount(raw: String): Double {
        val sanitized = raw.replace(",", "").trim()
        return sanitized.toDoubleOrNull() ?: 0.0
    }

    fun parseBkash(text: String, senderAlias: String? = null): ParsedSmsResult {
        val result = ParsedSmsResult(isValid = false, method = PaymentMethod.BKASH,
            detectedSenderAlias = senderAlias ?: "bkash", rawText = text)

        for (pattern in DISALLOWED_PATTERNS) {
            if (pattern.containsMatchIn(text)) {
                return result.copy(rejectionReason = "Ignored: matches disallowed pattern '${pattern.pattern}'")
            }
        }

        val hasIncoming = Regex("received|deposit|cash\\s*in", RegexOption.IGNORE_CASE).containsMatchIn(text)
        if (!hasIncoming) return result.copy(rejectionReason = "Missing incoming money indicator")

        val amountMatch = (Regex("(?:received\\s+Tk\\s*|Tk\\s*|Tk\\.\\s*)([\\d,]+(?:\\.\\d{1,2})?)", RegexOption.IGNORE_CASE).find(text)
            ?: Regex("amount\\s*(?:is)?\\s*Tk\\s*([\\d,]+(?:\\.\\d{1,2})?)", RegexOption.IGNORE_CASE).find(text))
        val amount = amountMatch?.groupValues?.getOrNull(1)?.let { cleanAmount(it) }

        val senderMatch = (Regex("from\\s+([+0-9]{11,14})", RegexOption.IGNORE_CASE).find(text)
            ?: Regex("sender\\s*(?:no\\.?|num\\.?|:)?\\s*([+0-9]{11,14})", RegexOption.IGNORE_CASE).find(text))
        val sender = senderMatch?.groupValues?.getOrNull(1)?.let { cleanPhoneNumber(it) }

        val trxMatch = (Regex("TrxID\\s+([A-Z0-9]+)", RegexOption.IGNORE_CASE).find(text)
            ?: Regex("Trx\\s*ID[:\\s]+([A-Z0-9]+)", RegexOption.IGNORE_CASE).find(text)
            ?: Regex("Transaction\\s*ID[:\\s]+([A-Z0-9]+)", RegexOption.IGNORE_CASE).find(text))
        val trxId = trxMatch?.groupValues?.getOrNull(1)?.uppercase()

        val dateMatch = Regex("at\\s+(\\d{1,2}/\\d{1,2}/\\d{2,4})\\s+(\\d{1,2}:\\d{2}(?::\\d{2})?(?:\\s*[AaPp][Mm])?)", RegexOption.IGNORE_CASE).find(text)
        val smsDate = dateMatch?.groupValues?.getOrNull(1)
        val smsTime = dateMatch?.groupValues?.getOrNull(2)

        if (trxId != null && amount != null && amount > 0 && sender != null) {
            return result.copy(isValid = true, trxId = trxId, amount = amount,
                senderNumber = sender, smsDate = smsDate, smsTime = smsTime)
        }

        val missing = buildList {
            if (trxId == null) add("TrxID")
            if (amount == null || amount <= 0) add("Amount")
            if (sender == null) add("Sender")
        }
        return result.copy(rejectionReason = "Incomplete bKash: missing ${missing.joinToString(", ")}")
    }

    fun parseNagad(text: String, senderAlias: String? = null): ParsedSmsResult {
        val result = ParsedSmsResult(isValid = false, method = PaymentMethod.NAGAD,
            detectedSenderAlias = senderAlias ?: "nagad", rawText = text)

        for (pattern in DISALLOWED_PATTERNS) {
            if (pattern.containsMatchIn(text)) {
                return result.copy(rejectionReason = "Ignored: matches disallowed pattern")
            }
        }

        val hasIncoming = Regex("received|cash\\s*in|deposit", RegexOption.IGNORE_CASE).containsMatchIn(text)
        if (!hasIncoming) return result.copy(rejectionReason = "Missing incoming money indicator")

        val amountMatch = (Regex("Received\\s*Amount:\\s*Tk\\s*([\\d,]+(?:\\.\\d{1,2})?)", RegexOption.IGNORE_CASE).find(text)
            ?: Regex("(?:received|Cash\\s*In)\\s*(?:Amount\\s*)?Tk\\s*([\\d,]+(?:\\.\\d{1,2})?)", RegexOption.IGNORE_CASE).find(text))
        val amount = amountMatch?.groupValues?.getOrNull(1)?.let { cleanAmount(it) }

        val senderMatch = (Regex("from\\s+([+0-9]{11,14})", RegexOption.IGNORE_CASE).find(text)
            ?: Regex("Sender:\\s*([+0-9]{11,14})", RegexOption.IGNORE_CASE).find(text))
        val sender = senderMatch?.groupValues?.getOrNull(1)?.let { cleanPhoneNumber(it) }

        val trxMatch = (Regex("TxnID[:\\s]+([A-Z0-9]+)", RegexOption.IGNORE_CASE).find(text)
            ?: Regex("Txn\\s*ID[:\\s]+([A-Z0-9]+)", RegexOption.IGNORE_CASE).find(text)
            ?: Regex("TrxID[:\\s]+([A-Z0-9]+)", RegexOption.IGNORE_CASE).find(text))
        val trxId = trxMatch?.groupValues?.getOrNull(1)?.uppercase()

        val dateMatch = Regex("at\\s+(\\d{1,2}/\\d{1,2}/\\d{2,4})\\s+(\\d{1,2}:\\d{2}(?::\\d{2})?)", RegexOption.IGNORE_CASE).find(text)
        val smsDate = dateMatch?.groupValues?.getOrNull(1)
        val smsTime = dateMatch?.groupValues?.getOrNull(2)

        if (trxId != null && amount != null && amount > 0 && sender != null) {
            return result.copy(isValid = true, trxId = trxId, amount = amount,
                senderNumber = sender, smsDate = smsDate, smsTime = smsTime)
        }

        val missing = buildList {
            if (trxId == null) add("TxnID")
            if (amount == null || amount <= 0) add("Amount")
            if (sender == null) add("Sender")
        }
        return result.copy(rejectionReason = "Incomplete Nagad: missing ${missing.joinToString(", ")}")
    }

    fun parseRocket(text: String, senderAlias: String? = null): ParsedSmsResult {
        val result = ParsedSmsResult(isValid = false, method = PaymentMethod.ROCKET,
            detectedSenderAlias = senderAlias ?: "rocket", rawText = text)

        for (pattern in DISALLOWED_PATTERNS) {
            if (pattern.containsMatchIn(text)) {
                return result.copy(rejectionReason = "Ignored: matches disallowed pattern")
            }
        }

        val hasIncoming = Regex("received|cash\\s*in|credit", RegexOption.IGNORE_CASE).containsMatchIn(text)
        if (!hasIncoming) return result.copy(rejectionReason = "Missing incoming money indicator")

        val amountMatch = (Regex("Tk\\s*([\\d,]+(?:\\.\\d{1,2})?)\\s*received", RegexOption.IGNORE_CASE).find(text)
            ?: Regex("(?:Cash\\s*In|Received)\\s*Tk\\s*([\\d,]+(?:\\.\\d{1,2})?)", RegexOption.IGNORE_CASE).find(text)
            ?: Regex("Tk\\s*([\\d,]+(?:\\.\\d{1,2})?)", RegexOption.IGNORE_CASE).find(text))
        val amount = amountMatch?.groupValues?.getOrNull(1)?.let { cleanAmount(it) }

        val senderMatch = Regex("from\\s+([+0-9]{11,14})", RegexOption.IGNORE_CASE).find(text)
        val sender = senderMatch?.groupValues?.getOrNull(1)?.let { cleanPhoneNumber(it) }

        val trxMatch = (Regex("TxnId[:\\s]+([A-Z0-9]+)", RegexOption.IGNORE_CASE).find(text)
            ?: Regex("TrxID[:\\s]+([A-Z0-9]+)", RegexOption.IGNORE_CASE).find(text)
            ?: Regex("Txn\\s*ID[:\\s]+([A-Z0-9]+)", RegexOption.IGNORE_CASE).find(text))
        val trxId = trxMatch?.groupValues?.getOrNull(1)?.uppercase()

        val dateMatch = (Regex("Date[:\\s]*(\\d{1,2}-?[A-Za-z]{3,4}-?\\d{2,4})\\s*(\\d{1,2}:\\d{2}(?::\\d{2})?)", RegexOption.IGNORE_CASE).find(text)
            ?: Regex("at\\s+(\\d{1,2}/\\d{1,2}/\\d{2,4})\\s+(\\d{1,2}:\\d{2}(?::\\d{2})?)", RegexOption.IGNORE_CASE).find(text))
        val smsDate = dateMatch?.groupValues?.getOrNull(1)
        val smsTime = dateMatch?.groupValues?.getOrNull(2)

        if (trxId != null && amount != null && amount > 0 && sender != null) {
            return result.copy(isValid = true, trxId = trxId, amount = amount,
                senderNumber = sender, smsDate = smsDate, smsTime = smsTime)
        }

        val missing = buildList {
            if (trxId == null) add("TxnId")
            if (amount == null || amount <= 0) add("Amount")
            if (sender == null) add("Sender")
        }
        return result.copy(rejectionReason = "Incomplete Rocket: missing ${missing.joinToString(", ")}")
    }

    fun parseIncoming(
        text: String,
        senderHeader: String? = null,
        bkashEnabled: Boolean = true,
        nagadEnabled: Boolean = true,
        rocketEnabled: Boolean = true
    ): ParsedSmsResult {
        val headerLower = (senderHeader ?: "").lowercase()
        val textLower = text.lowercase()

        val method: PaymentMethod? = when {
            headerLower.contains("bkash") || textLower.contains("bkash") ||
                    text.contains("TrxID") && text.contains("Balance Tk") -> PaymentMethod.BKASH
            headerLower.contains("nagad") || textLower.contains("nagad") ||
                    textLower.contains("received amount: tk") -> PaymentMethod.NAGAD
            headerLower.contains("rocket") || headerLower.contains("16216") ||
                    headerLower.contains("dbbl") || textLower.contains("rocket") ||
                    textLower.contains("to a/c") -> PaymentMethod.ROCKET
            text.contains("TrxID") && !text.contains("TxnID") -> PaymentMethod.BKASH
            text.contains("Received Amount: Tk") -> PaymentMethod.NAGAD
            text.contains("TxnId:") && text.contains("to A/C") -> PaymentMethod.ROCKET
            else -> null
        }

        if (method == null) {
            return ParsedSmsResult(isValid = false,
                rejectionReason = "Unsupported provider: not bKash, Nagad or Rocket",
                detectedSenderAlias = senderHeader ?: "UNKNOWN", rawText = text)
        }

        if (method == PaymentMethod.BKASH && !bkashEnabled)
            return ParsedSmsResult(isValid = false, method = method,
                rejectionReason = "bKash reader disabled in settings", rawText = text)
        if (method == PaymentMethod.NAGAD && !nagadEnabled)
            return ParsedSmsResult(isValid = false, method = method,
                rejectionReason = "Nagad reader disabled in settings", rawText = text)
        if (method == PaymentMethod.ROCKET && !rocketEnabled)
            return ParsedSmsResult(isValid = false, method = method,
                rejectionReason = "Rocket reader disabled in settings", rawText = text)

        return when (method) {
            PaymentMethod.BKASH -> parseBkash(text, senderHeader)
            PaymentMethod.NAGAD -> parseNagad(text, senderHeader)
            PaymentMethod.ROCKET -> parseRocket(text, senderHeader)
        }
    }
}
