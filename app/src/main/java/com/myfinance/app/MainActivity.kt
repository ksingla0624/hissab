package com.hissab.app
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.webkit.*
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var wv: WebView
    private var cb: ValueCallback<Array<Uri>>? = null

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        requestPermissions(arrayOf("android.permission.RECEIVE_SMS", "android.permission.READ_SMS") + (if (android.os.Build.VERSION.SDK_INT >= 33) arrayOf("android.permission.POST_NOTIFICATIONS") else arrayOf<String>()), 2)
        wv = WebView(this); setContentView(wv)
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.addJavascriptInterface(Bridge(), "Android")
        wv.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(v: WebView?, c: ValueCallback<Array<Uri>>?, p: FileChooserParams?): Boolean {
                cb?.onReceiveValue(null); cb = c
                startActivityForResult(p!!.createIntent(), 1); return true
            }
        }
        wv.loadUrl("file:///android_asset/index.html")
    }

    override fun onResume() { super.onResume(); wv.evaluateJavascript("try{pull()}catch(e){}", null) }

    override fun onActivityResult(r: Int, c: Int, d: Intent?) {
        if (r == 3 && c == RESULT_OK && d?.data != null) {
            val u = d.data!!
            contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            getSharedPreferences("p", 0).edit().putString("bk", u.toString()).apply()
            wv.evaluateJavascript("try{Android.persist(JSON.stringify(D));render()}catch(e){}", null)
        }
        if (r == 1) { cb?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(c, d)); cb = null }
    }

    inner class Bridge {
        @JavascriptInterface fun save(name: String, mime: String, text: String) {
            val v = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name); put(MediaStore.Downloads.MIME_TYPE, mime)
            }
            val u = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v)
            if (u != null) contentResolver.openOutputStream(u)?.use { it.write(text.toByteArray()) }
            runOnUiThread { Toast.makeText(this@MainActivity, "Saved to Downloads: $name", Toast.LENGTH_LONG).show() }
        }
        @JavascriptInterface fun persist(json: String) {
            File(filesDir, "hissab.json").writeText(json)
            getSharedPreferences("p", 0).getString("bk", null)?.let {
                try { contentResolver.openOutputStream(Uri.parse(it), "wt")?.use { o -> o.write(json.toByteArray()) } } catch (e: Exception) {}
            }
        }
        @JavascriptInterface fun load(): String { val f = File(filesDir, "hissab.json"); return if (f.exists()) f.readText() else "" }
        @JavascriptInterface fun status(): String = if (getSharedPreferences("p", 0).getString("bk", null) != null) "✔ Drive auto-backup is ON" else "Not set up yet"
        @JavascriptInterface fun pickBackup() {
            runOnUiThread {
                startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json")
                    .putExtra(Intent.EXTRA_TITLE, "hissab-data.json")
                    .addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION), 3)
            }
        }
        @JavascriptInterface fun takeSms(): String = Sms.take(this@MainActivity)
        @JavascriptInterface fun importOld(days: Int): String {
            val arr = JSONArray(); val since = System.currentTimeMillis() - days * 86400000L
            contentResolver.query(Uri.parse("content://sms/inbox"), arrayOf("address", "body", "date"), "date>?", arrayOf(since.toString()), "date DESC LIMIT 500")?.use { c ->
                while (c.moveToNext()) { val b = c.getString(1) ?: continue
                    if (Sms.looks(b) && Sms.senderOk(c.getString(0))) arr.put(JSONObject().put("s", c.getString(0)).put("b", b).put("t", c.getLong(2))) }
            }
            return arr.toString()
        }
        @JavascriptInterface fun schedule(json: String) { Sched.run(this@MainActivity, json) }
        @JavascriptInterface fun share(name: String, text: String) {
            val f = File(cacheDir, name); f.writeText(text)
            val u = FileProvider.getUriForFile(this@MainActivity, "com.hissab.app.fp", f)
            val i = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"; putExtra(Intent.EXTRA_STREAM, u); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runOnUiThread { startActivity(Intent.createChooser(i, "Back up to")) }
        }
    }
}