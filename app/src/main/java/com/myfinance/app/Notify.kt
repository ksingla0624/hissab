package com.myfinance.app
import android.app.*
import android.content.*
import org.json.JSONArray
import java.time.LocalDate
import java.time.ZoneId

object Sched {
    private fun pi(c: Context, code: Int, t: String, x: String) = PendingIntent.getBroadcast(c, code,
        Intent(c, Receiver::class.java).putExtra("t", t).putExtra("x", x), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun run(c: Context, json: String) {
        val sp = c.getSharedPreferences("p", 0)
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (i in 0 until sp.getInt("n", 0)) am.cancel(pi(c, i, "", ""))
        sp.edit().putString("j", json).apply()
        val a = JSONArray(json); var n = 0; val now = System.currentTimeMillis()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i); val d = LocalDate.parse(o.getString("d"))
            for (off in setOf(o.optInt("k"), 0)) {
                val t = d.minusDays(off.toLong()).atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                if (t > now) {
                    val msg = (if (off == 0) "Due today: " else "Due in $off day(s): ") + "₹" + o.get("a") + " — " + o.getString("t")
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi(c, n, "Payment due", msg)); n++
                }
            }
        }
        sp.edit().putInt("n", n).apply()
    }
}

class Receiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("due", "Payment reminders", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        nm.notify(System.nanoTime().toInt(), Notification.Builder(c, "due").setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(i.getStringExtra("t")).setContentText(i.getStringExtra("x")).setContentIntent(open).setAutoCancel(true).build())
    }
}

class Boot : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) { c.getSharedPreferences("p", 0).getString("j", null)?.let { Sched.run(c, it) } }
}
