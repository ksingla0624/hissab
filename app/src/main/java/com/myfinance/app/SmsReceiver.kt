package com.hissab.app
import android.app.*
import android.content.*
import android.provider.Telephony
import org.json.JSONArray
import org.json.JSONObject

object Sms {
    private val RX = Regex("(debited|credited|deposited|withdrawn|spent|received)", RegexOption.IGNORE_CASE)
    private val AM = Regex("(inr|rs\\.?|₹)", RegexOption.IGNORE_CASE)
    private val OTP = Regex("\\botp\\b", RegexOption.IGNORE_CASE)
    fun looks(b: String) = RX.containsMatchIn(b) && AM.containsMatchIn(b) && !OTP.containsMatchIn(b)
    fun add(c: Context, s: String, b: String, t: Long) {
        val sp = c.getSharedPreferences("p", 0)
        val a = JSONArray(sp.getString("pend", "[]"))
        a.put(JSONObject().put("s", s).put("b", b).put("t", t))
        sp.edit().putString("pend", a.toString()).apply()
    }
    fun take(c: Context): String {
        val sp = c.getSharedPreferences("p", 0)
        val v = sp.getString("pend", "[]") ?: "[]"
        sp.edit().putString("pend", "[]").apply(); return v
    }
}

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val m = Telephony.Sms.Intents.getMessagesFromIntent(i) ?: return
        if (m.isEmpty()) return
        val body = m.joinToString("") { it.messageBody ?: "" }
        if (!Sms.looks(body)) return
        Sms.add(c, m[0].originatingAddress ?: "", body, System.currentTimeMillis())
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("sms", "Bank SMS", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(c, 1, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        nm.notify(System.nanoTime().toInt(), Notification.Builder(c, "sms").setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Bank transaction detected").setContentText("Tap to review and approve in Hisaab")
            .setContentIntent(open).setAutoCancel(true).build())
    }
}
