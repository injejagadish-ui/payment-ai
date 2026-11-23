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

private const val PREF = "secure_payment_storage"
private const val KEY = "encrypted_payments"

private fun preferences(context: Context) =
    context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

@Synchronized
fun all(context: Context): List<Payment> {
    return try {
        val encrypted = preferences(context)
            .getString(KEY, null) ?: return emptyList()

        val json = SecureStorage.decrypt(encrypted)
        val array = JSONArray(json)

        val result = mutableListOf<Payment>()

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

        result.sortedByDescending { it.time }

    } catch (e: Exception) {
        android.util.Log.e(
            "PaymentStore",
            "Unable to decrypt payment history",
            e
        )
        emptyList()
    }
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

    val duplicate = old.any {
        it.source == source &&
        it.amount == amount &&
        it.description == description &&
        kotlin.math.abs(it.time - time) < 60000
    }

    if (duplicate) return false

    val payments = (
        listOf(
            Payment(amount, source, description, time)
        ) + old
    ).take(1000)

    val array = JSONArray()

    payments.forEach {
        array.put(
            JSONObject().apply {
                put("amount", it.amount)
                put("source", it.source)
                put("description", it.description)
                put("time", it.time)
            }
        )
    }

    return try {
        val encrypted = SecureStorage.encrypt(array.toString())

        preferences(context).edit()
            .putString(KEY, encrypted)
            .commit()

    } catch (e: Exception) {
        android.util.Log.e(
            "PaymentStore",
            "Encryption failed",
            e
        )
        false
    }
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
