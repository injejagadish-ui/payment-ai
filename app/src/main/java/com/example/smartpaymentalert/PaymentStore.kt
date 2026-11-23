
package com.example.smartpaymentalert

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

object PaymentStore {

    data class Payment(
        val amount: Double,
        val source: String,
        val description: String,
        val time: Long
    )

    private const val PREF = "payment_storage"
    private const val KEY = "payments"

    @Synchronized
    fun all(context: Context): List<Payment> {
        val json = context.getSharedPreferences(
            PREF, Context.MODE_PRIVATE
        ).getString(KEY, "[]") ?: "[]"

        val result = mutableListOf<Payment>()
        val array = JSONArray(json)

        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            result.add(
                Payment(
                    item.getDouble("amount"),
                    item.getString("source"),
                    item.getString("description"),
                    item.getLong("time")
                )
            )
        }

        return result.sortedByDescending { it.time }
    }

    @Synchronized
    fun add(
        context: Context,
        amount: Double,
        source: String,
        description: String,
        time: Long
    ): Boolean {
        val old = all(context)

        // Avoid duplicate payment alerts.
        val duplicate = old.any {
            it.source == source &&
            it.amount == amount &&
            it.description == description &&
            kotlin.math.abs(it.time - time) < 60000
        }

        if (duplicate) return false

        val array = JSONArray()

        (listOf(
            Payment(amount, source, description, time)
        ) + old).take(1000).forEach {
            array.put(
                JSONObject().apply {
                    put("amount", it.amount)
                    put("source", it.source)
                    put("description", it.description)
                    put("time", it.time)
                }
            )
        }

        context.getSharedPreferences(
            PREF, Context.MODE_PRIVATE
        ).edit().putString(KEY, array.toString()).apply()

        return true
    }

    fun todayTotal(context: Context): Double {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        val start = calendar.timeInMillis

        return all(context)
            .filter { it.time >= start }
            .sumOf { it.amount }
    }
}
