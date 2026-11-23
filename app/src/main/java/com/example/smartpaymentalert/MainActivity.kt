package com.example.smartpaymentalert

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var content: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                100
            )
        }

        buildDashboard()
    }

    private fun buildDashboard() {
        val scroll = ScrollView(this)

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
            setBackgroundColor(0xFFF6F5FA.toInt())
        }

        scroll.addView(content)
        setContentView(scroll)

        addText(
            "SMART PAYMENT ALERT",
            24f,
            0xFF4527A0.toInt(),
            true
        )

        addText(
            "Your payment collection dashboard",
            14f,
            0xFF666666.toInt()
        )

        addButton("Enable Payment Detection") {
            startActivity(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            )
        }

        addButton("Refresh Dashboard") {
            buildDashboard()
        }

        val total = PaymentStore.todayTotal(this)

        addText("TODAY'S COLLECTION", 15f, 0xFF555555.toInt(), true)

        addText(
            "₹" + String.format(Locale.US, "%.2f", total),
            32f,
            0xFF168447.toInt(),
            true
        )

        val payments = PaymentStore.all(this)

        addText(
            "Total detected payments: ${payments.size}",
            16f,
            0xFF333333.toInt(),
            true
        )

        addText("PAYMENT HISTORY", 20f, 0xFF4527A0.toInt(), true)

        if (payments.isEmpty()) {
            addText(
                "No payments detected yet.",
                15f,
                0xFF777777.toInt()
            )
        }

        val dateFormat = SimpleDateFormat(
            "dd MMM yyyy, hh:mm a",
            Locale.getDefault()
        )

        payments.take(50).forEach { payment ->

            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                setBackgroundColor(0xFFFFFFFF.toInt())
            }

            val amount = TextView(this).apply {
                text = "+ ₹" + String.format(
                    Locale.US, "%.2f", payment.amount
                )
                textSize = 20f
                setTextColor(0xFF168447.toInt())
            }

            val source = TextView(this).apply {
                text = payment.source
                textSize = 15f
                setTextColor(0xFF333333.toInt())
            }

            val date = TextView(this).apply {
                text = dateFormat.format(Date(payment.time))
                textSize = 12f
                setTextColor(0xFF777777.toInt())
            }

            item.addView(amount)
            item.addView(source)
            item.addView(date)

            content.addView(
                item,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(10)
                }
            )
        }

        addText(
            "Payments are stored locally on this device.",
            12f,
            0xFF777777.toInt()
        )
    }

    private fun addText(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false
    ) {
        val view = TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)

            if (bold) {
                setTypeface(null, android.graphics.Typeface.BOLD)
            }

            setPadding(0, dp(8), 0, dp(8))
        }

        content.addView(view)
    }

    private fun addButton(
        label: String,
        action: () -> Unit
    ) {
        val button = Button(this).apply {
            text = label
            setOnClickListener { action() }
        }

        content.addView(
            button,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onResume() {
        super.onResume()

        if (::content.isInitialized) {
            buildDashboard()
        }
    }
}
