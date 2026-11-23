
package com.example.smartpaymentalert

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import java.util.Locale

class PaymentNotificationListener : NotificationListenerService() {

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private val paymentApps = setOf(
        "com.phonepe.app",
        "com.google.android.apps.nbu.paisa.user",
        "net.one97.paytm"
    )

    private val incomingWords = listOf(
        "credited",
        "received",
        "deposited",
        "money received",
        "upi credit",
        "credit alert"
    )

    private val outgoingWords = listOf(
        "debited",
        "withdrawn",
        "sent to",
        "paid to",
        "payment to",
        "purchase"
    )

    private val amountPattern = Regex(
        """(?i)(?:₹|rs\.?|inr)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)|(?:credited|received|deposited)\D{0,18}([0-9][0-9,]*(?:\.[0-9]{1,2})?)"""
    )

    override fun onCreate() {
        super.onCreate()

        createChannel()

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(
                    Locale("en", "IN")
                )
                ttsReady =
                    result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }
    }

    override fun onNotificationPosted(
        sbn: StatusBarNotification
    ) {
        try {
            val extras = sbn.notification.extras

            val title = extras.getCharSequence(
                Notification.EXTRA_TITLE
            )?.toString() ?: ""

            val text = extras.getCharSequence(
                Notification.EXTRA_TEXT
            )?.toString() ?: ""

            val bigText = extras.getCharSequence(
                Notification.EXTRA_BIG_TEXT
            )?.toString() ?: ""

            val subText = extras.getCharSequence(
                Notification.EXTRA_SUB_TEXT
            )?.toString() ?: ""

            val message = listOf(
                title, text, bigText, subText
            ).filter { it.isNotBlank() }
                .distinct()
                .joinToString(" ")

            processPayment(
                sbn.packageName,
                message,
                sbn.postTime
            )

        } catch (e: Exception) {
            android.util.Log.e(
                "PaymentAlert",
                "Notification error",
                e
            )
        }
    }

    private fun processPayment(
        packageName: String,
        message: String,
        time: Long
    ) {
        if (message.isBlank()) return

        val lower = message.lowercase(Locale.ROOT)

        val isKnownApp = packageName in paymentApps

        val hasBankWords = listOf(
            "bank", "a/c", "account", "upi"
        ).any { lower.contains(it) }

        val isLikelyBank = hasBankWords &&
            incomingWords.any { lower.contains(it) }

        if (!isKnownApp && !isLikelyBank) return

        // Reject outgoing transactions.
        if (outgoingWords.any { lower.contains(it) }) return

        // Only accept likely incoming transactions.
        if (incomingWords.none { lower.contains(it) }) return

        val match = amountPattern.find(message) ?: return

        val amountText = match.groups[1]?.value
            ?: match.groups[2]?.value
            ?: return

        val amount = amountText
            .replace(",", "")
            .toDoubleOrNull() ?: return

        if (amount <= 0 || !amount.isFinite()) return

        val source = when (packageName) {
            "com.phonepe.app" -> "PhonePe"
            "com.google.android.apps.nbu.paisa.user" ->
                "Google Pay"
            "net.one97.paytm" -> "Paytm"
            else -> "Bank / Other UPI"
        }

        val added = PaymentStore.add(
            this,
            amount,
            source,
            message,
            time
        )

        if (!added) return

        val spokenAmount = String.format(
            Locale.US, "%.2f", amount
        )

        val speech = "You have received $spokenAmount rupees"

        showPaymentNotification(
            source,
            spokenAmount
        )

        if (ttsReady) {
            tts?.speak(
                speech,
                TextToSpeech.QUEUE_ADD,
                null,
                "payment_$time"
            )
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "payment_alerts",
                "Payment Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )

            channel.description =
                "Notifications for received payments"

            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun showPaymentNotification(
        source: String,
        amount: String
    ) {
        val intent = Intent(
            this,
            MainActivity::class.java
        )

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(
            this,
            "payment_alerts"
        )
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Payment Received")
            .setContentText("₹$amount received via $source")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            getSystemService(NotificationManager::class.java)
                .notify(
                    System.currentTimeMillis().toInt(),
                    notification
                )
        } catch (e: SecurityException) {
            android.util.Log.e(
                "PaymentAlert",
                "Notification permission missing",
                e
            )
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }
}
