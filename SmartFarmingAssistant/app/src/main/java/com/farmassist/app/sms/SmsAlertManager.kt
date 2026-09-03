package com.farmassist.app.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import androidx.core.content.ContextCompat

/**
 * Sends SMS directly via the phone's own SIM — no internet, no separate GSM
 * hardware. This is the fallback alert path: it must work even with zero
 * data connectivity (airplane mode with SIM active for calls/SMS still works).
 */
class SmsAlertManager(private val context: Context) {

    fun hasSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Sends a plain-language alert to the registered farmer phone number.
     * Returns true if the send was attempted (SmsManager doesn't give a synchronous
     * delivery guarantee — for production, register a PendingIntent-based
     * delivery/sent receiver to confirm).
     */
    fun sendAlert(phoneNumber: String, message: String): Boolean {
        if (!hasSmsPermission() || phoneNumber.isBlank()) return false

        return try {
            val smsManager = SmsManager.getDefault()
            // Split long messages automatically so advisory text isn't truncated.
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            true
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        fun buildDiseaseAlertText(className: String, confidencePct: Int, recommendation: String): String =
            "FarmAssist Alert: $className detected ($confidencePct% confidence). $recommendation"

        fun buildIrrigationAlertText(advice: String, soilMoisture: Float): String =
            "FarmAssist Alert: $advice (soil moisture ${soilMoisture.toInt()}%)"
    }
}
fun requestSmsPermission(activity: android.app.Activity, requestCode: Int = 101) {
    androidx.core.app.ActivityCompat.requestPermissions(
        activity, arrayOf(Manifest.permission.SEND_SMS), requestCode
    )
}