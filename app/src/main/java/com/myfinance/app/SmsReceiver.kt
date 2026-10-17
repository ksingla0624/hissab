package com.hissab.app
import android.app.*
import android.content.*
import android.provider.Telephony
import org.json.JSONArray
import org.json.JSONObject

object Sms {
    private val I = RegexOption.IGNORE_CASE
    private val NOTX = Regex("\\b(otp|one time password|verification code|failed|declined|unsuccessful|insufficient|could not|will be debited|to be debited|will be credited|is due|min(?:imum)? due|due on|due date|statement|pre-?approved|eligible|congratulations|apply now|click here|requested money|collect request|has requested|request of)\\b", I)
    private val CR = Regex("\\b(credited|deposited|received|refunded|reversed)\\b|(?<=[\\d.,]\\s?)\\bcr\\b", I)
    private val DR = Regex("\\b(debited|withdrawn|withdrew|spent|paid|purchased|sent|transferred|deducted|charged)\\b|(?<=[\\d.,]\\s?)\\bdr\\b", I)
    private val AMT = Regex("(?:INR|Rs\\.?|₹)[:\\s\\-]*([\\d,]+(?:\\.\\d+)?)", I)
    private val L4 = Regex("\\b(?:a/c|a\\.c|acct?|account|card|ending|ac)\\b\\s*(?:no\\.?|number|ending|with)?\\s*[:\\-]?\\s*[xX*]*\\s*(\\d{3,4})\\b", I)
    private val L4B = Regex("[xX*]{2,}\\s?(\\d{3,4})\\b", I)
    private val REF = Regex("UPI[:/ \\-]*(?:P2[AP]/)?(\\d{9,})|(?:ref(?:erence)?(?: no\\.?)?|utr(?: no\\.?)?|rrn|txn(?: ?id| ?no\\.?)?)[:.\\-\\s#]*(\\d{6,}|[A-Z]{2,5}\\d{6,})", I)
    private val UPI = Regex("\\bupi\\b", I)
    private val BAL = Regex("\\b(avl|available|bal|balance)\\b", I)
    private val PROMO = Regex("\\b(offers?|up ?to|win|discount|coupons?|vouchers?|rewards?|t&cs?|tncs?|apply|avail|spend|get|exclusive|hurry|congrats|limited period|valid (?:till|until)|shop now|book now|no cost emi|pre-?approved|click|download|refer|earn|lucky)\\b|%\\s?off|\\boff\\b|https?:|www\\.|bit\\.ly", I)
    private val PHONE = Regex("^\\+?[\\d\\s-]+$", I)
    private val WS = Regex("\\s+")
    fun senderOk(s: String?): Boolean { val x = (s ?: "").trim(); return !(PHONE.matches(x) || x.endsWith("-P", true)) }
    /** Mirrors the Inbox parser in index.html: a notification is raised only if the message will reach the Inbox. */
    fun accept(s: String?, body: String): Boolean {
        if (!senderOk(s)) return false
        val t = body.replace(WS, " ").trim()
        if (NOTX.containsMatchIn(t) || !AMT.containsMatchIn(t)) return false
        if (!CR.containsMatchIn(t) && !DR.containsMatchIn(t)) return false
        val l4 = L4.containsMatchIn(t) || L4B.containsMatchIn(t)
        val ref = REF.containsMatchIn(t)
        if (!l4 && !ref && !UPI.containsMatchIn(t)) return false
        if (PROMO.containsMatchIn(t) && !(l4 && (BAL.containsMatchIn(t) || ref))) return false
        return true
    }
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
        if (!Sms.accept(m[0].originatingAddress, body)) return
        Sms.add(c, m[0].originatingAddress ?: "", body, System.currentTimeMillis())
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("sms", "Bank SMS", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(c, 1, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        nm.notify(System.nanoTime().toInt(), Notification.Builder(c, "sms").setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Bank transaction detected").setContentText("Tap to review and approve in Hisaab")
            .setContentIntent(open).setAutoCancel(true).build())
    }
}