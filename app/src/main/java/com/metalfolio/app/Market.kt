package com.metalfolio.app

import android.content.Context
import android.net.Uri
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

// Only the watchlist and latest successful quote snapshot are stored locally.
data class Asset(val symbol: String, val label: String, val type: String, val currency: String = "USD")
data class Quote(val price: Double, val prior: Double?, val fetchedAt: Long)

val defaults = listOf(
    Asset("XAU/USD", "Gold spot", "Metals"), Asset("XAG/USD", "Silver spot", "Metals"),
    Asset("VOO", "Vanguard S&P 500", "ETFs"), Asset("QQQ", "Nasdaq 100 ETF", "ETFs"),
    Asset("VWRA", "Vanguard All-World (LSE)", "ETFs"),
    Asset("AAPL", "Apple", "Stocks"), Asset("MSFT", "Microsoft", "Stocks"),
    Asset("NVDA", "Nvidia", "Stocks")
)

class MarketStore(context: Context) {
    private val prefs = context.getSharedPreferences("metalfolio_v1", Context.MODE_PRIVATE)
    var apiKey: String
        get() = prefs.getString("api_key", "") ?: ""
        set(value) { prefs.edit().putString("api_key", value.trim()).apply() }
    var unit: String
        get() = prefs.getString("unit", "oz") ?: "oz"
        set(value) { prefs.edit().putString("unit", value).apply() }
    var displayCurrency: String
        get() = prefs.getString("display_currency", "Native") ?: "Native"
        set(value) { prefs.edit().putString("display_currency", value).apply() }
    var lastError: String
        get() = prefs.getString("error", "") ?: ""
        set(value) { prefs.edit().putString("error", value).apply() }
    var lastSync: Long
        get() = prefs.getLong("last_sync", 0L)
        set(value) { prefs.edit().putLong("last_sync", value).apply() }
    fun assets(): List<Asset> = try {
        val raw = prefs.getString("assets", null) ?: return defaults
        val arr = JSONArray(raw)
        (0 until arr.length()).map { i ->
            val x = arr.getJSONObject(i)
            Asset(x.getString("s"), x.getString("n"), x.getString("t"), x.optString("c", "USD"))
        }
    } catch (_: Exception) { defaults }
    fun saveAssets(assets: List<Asset>) {
        val arr = JSONArray()
        assets.forEach { arr.put(JSONObject().put("s", it.symbol).put("n", it.label).put("t", it.type).put("c", it.currency)) }
        prefs.edit().putString("assets", arr.toString()).apply()
    }
    fun quotes(): Map<String, Quote> = try {
        val raw = JSONObject(prefs.getString("quotes", "{}") ?: "{}")
        raw.keys().asSequence().associateWith { symbol ->
            val q = raw.getJSONObject(symbol)
            Quote(q.getDouble("p"), if (q.isNull("prev")) null else q.getDouble("prev"), q.getLong("ts"))
        }
    } catch (_: Exception) { emptyMap() }
    fun mergeQuotes(prices: Map<String, Double>) {
        val existing = quotes()
        val obj = JSONObject()
        val now = System.currentTimeMillis()
        (existing.keys + prices.keys).forEach { key ->
            val old = existing[key]
            val price = prices[key] ?: old?.price ?: return@forEach
            val previous = if (prices.containsKey(key) && old != null && old.price != price) old.price else old?.prior
            obj.put(key, JSONObject().put("p", price).put("prev", previous).put("ts", if (prices.containsKey(key)) now else old?.fetchedAt ?: now))
        }
        prefs.edit().putString("quotes", obj.toString()).putLong("last_sync", now).putString("error", "").apply()
    }
}

// Batch the requests; price data is provider-dependent and can be delayed or restricted.
object MarketApi {
    suspend fun fetch(key: String, symbols: List<String>): Pair<Map<String, Double>, String?> = withContext(Dispatchers.IO) {
        if (symbols.isEmpty()) return@withContext emptyMap<String, Double>() to null
        val found = mutableMapOf<String, Double>()
        var error: String? = null
        for ((index, chunk) in symbols.distinct().chunked(8).withIndex()) { // Free-tier rate budget: 8 credits/minute.
            if (index > 0) delay(61_000) // Avoid throttling across batches.
            val url = "https://api.twelvedata.com/price?symbol=${Uri.encode(chunk.joinToString(","), ",/")}&apikey=${Uri.encode(key)}"
            try {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 12000; readTimeout = 12000; requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                }
                try {
                    if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}")
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    if (json.has("status") && json.optString("status") == "error") {
                        error = json.optString("message", "Provider rejected request")
                    } else if (chunk.size == 1 && json.has("price")) {
                        json.optString("price").toDoubleOrNull()?.takeIf { it > 0 }?.let { found[chunk[0]] = it }
                    } else {
                        chunk.forEach { symbol ->
                            val item = json.optJSONObject(symbol)
                            if (item?.optString("status") == "error") {
                                error = "Some symbols are unavailable on your data plan"
                            }
                            item?.optString("price")?.toDoubleOrNull()?.takeIf { it > 0 }?.let { found[symbol] = it }
                        }
                    }
                } finally { conn.disconnect() }
            } catch (e: Exception) { error = e.message ?: "Network error" }
        }
        found to error
    }
}

class QuoteWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val store = MarketStore(applicationContext)
        if (store.apiKey.isEmpty()) return Result.success()
        val (quotes, error) = MarketApi.fetch(store.apiKey, store.assets().map { it.symbol })
        if (quotes.isNotEmpty()) store.mergeQuotes(quotes)
        if (error != null) store.lastError = error
        return if (quotes.isEmpty() && error != null) Result.retry() else Result.success()
    }
}

fun scheduleSync(context: Context) {
    val req = PeriodicWorkRequestBuilder<QuoteWorker>(1, TimeUnit.HOURS)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork("hourly_quotes", ExistingPeriodicWorkPolicy.KEEP, req)
}
