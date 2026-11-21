package com.metalfolio.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private val bg = Color(0xFF0B1017)
private val surface = Color(0xFF151C25)
private val raised = Color(0xFF1B2530)
private val ink = Color(0xFFEAF0F5)
private val muted = Color(0xFF91A0AF)
private val accent = Color(0xFFB9E3CF)
private val good = Color(0xFF76D5AC)
private val bad = Color(0xFFF18B91)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scheduleSync(this)
        val store = MarketStore(this)
        setContent {
            var assets by remember { mutableStateOf(store.assets()) }
            var quotes by remember { mutableStateOf(store.quotes()) }
            var category by remember { mutableStateOf("All") }
            var screen by remember { mutableStateOf("home") }
            var key by remember { mutableStateOf(store.apiKey) }
            var unit by remember { mutableStateOf(store.unit) }
            var displayCurrency by remember { mutableStateOf(store.displayCurrency) }
            var syncAt by remember { mutableLongStateOf(store.lastSync) }
            var error by remember { mutableStateOf(store.lastError) }
            var busy by remember { mutableStateOf(false) }
            val refresh = {
                if (!busy && key.isNotBlank()) {
                    busy = true
                    lifecycleScope.launch {
                        val (newPrices, apiError) = MarketApi.fetch(key, assets.map { it.symbol })
                        if (newPrices.isNotEmpty()) store.mergeQuotes(newPrices)
                        if (apiError != null) store.lastError = apiError
                        quotes = store.quotes(); syncAt = store.lastSync
                        error = apiError ?: if (newPrices.isEmpty()) "No prices returned" else ""
                        busy = false
                    }
                }
                Unit
            }
            MaterialTheme(colorScheme = darkColorScheme(primary = accent, background = bg, surface = surface, onBackground = ink, onSurface = ink)) {
                Column(Modifier.fillMaxSize().background(bg).statusBarsPadding().navigationBarsPadding()) {
                    when(screen) {
                        "home" -> {
                            Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 16.dp, top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("METALFOLIO", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.4.sp)
                                    Text("Your markets.", fontSize = 29.sp, fontWeight = FontWeight.SemiBold, color = ink)
                                }
                                IconButton(onClick = { screen = "settings" }) { Icon(Icons.Default.Tune, "Settings", tint = muted) }
                            }
                            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(if(syncAt == 0L) "No live prices yet" else "Updated ${stamp(syncAt)}", fontSize = 12.sp, color = muted)
                                    Text("Hourly sync • No live streaming", fontSize = 11.sp, color = muted)
                                }
                                OutlinedButton(onClick = refresh, enabled = !busy && key.isNotBlank(), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Color(0xFF394B54))) {
                                    if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                    else Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(7.dp)); Text("Refresh")
                                }
                            }
                            if (key.isBlank()) {
                                Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).background(raised, RoundedCornerShape(16.dp)).clickable { screen = "settings" }.padding(16.dp)) {
                                    Column { Text("Connect market data", color = accent, fontWeight = FontWeight.SemiBold); Text("Add your free Twelve Data API key to load real prices.", color = muted, fontSize = 13.sp) }
                                }
                            }
                            if (error.isNotBlank()) Text(error, color = bad, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp))
                            CategorySelector(category, listOf("All", "Metals", "ETFs", "Stocks")) { category = it }
                            val visible = assets.filter { category == "All" || it.type == category }
                            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("WATCHLIST", color = muted, fontSize = 11.sp, letterSpacing = 1.5.sp, modifier = Modifier.weight(1f))
                                Text("${visible.size} assets", color = muted, fontSize = 12.sp)
                            }
                            PullToRefreshBox(
                                isRefreshing = busy,
                                onRefresh = { if (key.isNotBlank()) refresh() },
                                modifier = Modifier.weight(1f)
                            ) {
                                LazyColumn(
                                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 2.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(visible, key = { it.symbol }) { asset ->
                                        AssetRow(asset, quotes[asset.symbol], unit, displayCurrency)
                                    }
                                    if (visible.isEmpty()) item {
                                        Text("Nothing here yet. Add an asset in Customize.", color = muted, modifier = Modifier.padding(14.dp))
                                    }
                                    item { Spacer(Modifier.height(12.dp)) }
                                }
                            }
                            Button(onClick = { screen = "edit" }, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = bg)) {
                                Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Customize watchlist", fontWeight = FontWeight.Bold)
                            }
                        }
                        "edit" -> WatchlistScreen(assets, back = { screen = "home" }, onUpdate = { assets = it; store.saveAssets(it) })
                        "settings" -> SettingsScreen(key, unit, displayCurrency, back = { screen = "home" }, onKey = { key = it; store.apiKey = it }, onUnit = { unit = it; store.unit = it }, onCurrency = { displayCurrency = it; store.displayCurrency = it }, onRefresh = refresh)
                    }
                }
            }
        }
    }
}

@Composable private fun CategorySelector(value: String, options: List<String>, change: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        options.forEach { item ->
            val selected = item == value
            Surface(onClick = { change(item) }, color = if (selected) accent else surface, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                Text(item, modifier = Modifier.padding(vertical = 12.dp), color = if(selected) bg else muted, fontSize = 12.sp, fontWeight = if(selected) FontWeight.Bold else FontWeight.Medium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

@Composable private fun AssetRow(asset: Asset, quote: Quote?, unit: String, currency: String) {
    val metal = asset.type == "Metals"
    val p = quote?.price?.let { if (metal && unit == "g") it / 31.1034768 else it }
    val prev = quote?.prior?.let { if (metal && unit == "g") it / 31.1034768 else it }
    // USD/AED conversion is only shown for USD-denominated assets (approximate peg).
    val converted = currency == "AED" && asset.currency == "USD"
    val multiplier = if(converted) 3.6725 else 1.0
    val shownCurrency = if (converted) "AED" else asset.currency
    val diff = if (p != null && prev != null && prev > 0) (p - prev) / prev * 100 else null
    Surface(color = surface, shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(43.dp).background(if(metal) Color(0xFF323229) else raised, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                Text(if(asset.symbol.startsWith("XAU")) "Au" else if(asset.symbol.startsWith("XAG")) "Ag" else if(asset.type == "ETFs") "◇" else "↗", color = if(metal) Color(0xFFE5CE88) else accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(asset.symbol, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ink, maxLines = 1)
                Text(asset.label, fontSize = 11.sp, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(if(p == null) "—" else "%s %,.2f".format(Locale.US, shownCurrency, p * multiplier), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ink)
                Text(if(metal) "per ${if(unit == "g") "gram" else "troy oz"}" else "per share", fontSize = 10.sp, color = muted)
                if (diff != null) Text("%+.2f%% since prior price".format(Locale.US, diff), fontSize = 10.sp, color = if(diff >= 0) good else bad)
                else if (quote != null) Text("Latest available quote", color = muted, fontSize = 10.sp)
            }
        }
    }
}

@Composable private fun Header(title: String, back: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = ink) }
        Text(title, fontSize = 23.sp, fontWeight = FontWeight.SemiBold, color = ink)
    }
}

@Composable private fun WatchlistScreen(assets: List<Asset>, back: () -> Unit, onUpdate: (List<Asset>) -> Unit) {
    var showAdd by remember { mutableStateOf(false) }
    var group by remember { mutableStateOf("Metals") }
    var symbol by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var quoteCurrency by remember { mutableStateOf("USD") }
    var menu by remember { mutableStateOf(false) }
    val suggestions = when(group) {
        "Metals" -> listOf(Asset("XAU/USD", "Gold spot", group), Asset("XAG/USD", "Silver spot", group), Asset("XPT/USD", "Platinum spot", group))
        "ETFs" -> listOf(Asset("VOO", "Vanguard S&P 500", group), Asset("QQQ", "Nasdaq 100 ETF", group), Asset("VT", "Vanguard Total World", group), Asset("VWRA", "Vanguard All-World (LSE)", group), Asset("CSPX", "iShares Core S&P 500 (LSE)", group))
        else -> listOf(Asset("AAPL", "Apple", group), Asset("MSFT", "Microsoft", group), Asset("NVDA", "Nvidia", group), Asset("ASML", "ASML", group), Asset("TSM", "TSMC", group))
    }
    Header("Your watchlist", back)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Choose what appears on your home screen. Swipe-free, distraction-free.", fontSize = 13.sp, color = muted) }
        items(assets, key = { it.symbol }) { a ->
            Surface(color = surface, shape = RoundedCornerShape(15.dp)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(a.symbol, color = ink, fontWeight = FontWeight.Bold); Text("${a.label} · ${a.type}", fontSize = 12.sp, color = muted) }
                    IconButton(onClick = { onUpdate(assets.filterNot { it.symbol == a.symbol }) }) { Icon(Icons.Default.Close, "Remove ${a.symbol}", tint = muted) }
                }
            }
        }
        item {
            HorizontalDivider(color = raised)
            Text("ADD ASSET", color = accent, fontSize = 12.sp, letterSpacing = 2.sp, modifier = Modifier.padding(top = 14.dp))
            Box {
                OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) { Text("Category: $group"); Spacer(Modifier.weight(1f)); Icon(Icons.Default.ArrowDropDown, null) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    listOf("Metals", "ETFs", "Stocks").forEach { category -> DropdownMenuItem(text = { Text(category) }, onClick = { group = category; menu = false }) }
                }
            }
            suggestions.forEach { candidate ->
                if (assets.none { it.symbol == candidate.symbol }) {
                    TextButton(onClick = { onUpdate(assets + candidate) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(7.dp)); Text("${candidate.symbol}  ·  ${candidate.label}", modifier = Modifier.weight(1f), color = accent)
                    }
                }
            }
            OutlinedTextField(value = symbol, onValueChange = { symbol = it.uppercase(Locale.US).trim().take(30) }, label = { Text("Custom ticker (e.g. VUAA, 7203, XAU/USD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = name, onValueChange = { name = it.take(60) }, label = { Text("Display name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = quoteCurrency, onValueChange = { quoteCurrency = it.uppercase(Locale.US).take(6) }, label = { Text("Quoted currency (e.g. USD, EUR, GBP)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("For non-US listings, use the provider's exact symbol and currency. Availability depends on your plan.", fontSize = 11.sp, color = muted)
            Button(onClick = {
                val s = symbol.trim(); if (s.isNotBlank() && assets.none { it.symbol == s }) {
                    onUpdate(assets + Asset(s, name.ifBlank { s }, group, quoteCurrency.ifBlank { "USD" }))
                    symbol = ""; name = ""
                }
            }, enabled = symbol.isNotBlank() && assets.none { it.symbol == symbol }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp)) { Text("Add custom symbol") }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable private fun SettingsScreen(key: String, unit: String, currency: String, back: () -> Unit, onKey: (String) -> Unit, onUnit: (String) -> Unit, onCurrency: (String) -> Unit, onRefresh: () -> Unit) {
    var isVisible by remember { mutableStateOf(false) }
    Header("Preferences", back)
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("MARKET DATA", fontSize = 12.sp, color = accent, letterSpacing = 2.sp)
        OutlinedTextField(value = key, onValueChange = onKey, label = { Text("Twelve Data API key") }, visualTransformation = if(isVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(), trailingIcon = { IconButton(onClick = { isVisible = !isVisible }) { Icon(if(isVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) } }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Text("Get a personal API key at twelvedata.com. Stored locally on this device, not embedded in the app.", fontSize = 12.sp, color = muted)
        HorizontalDivider(color = raised)
        Text("METALS UNIT", fontSize = 12.sp, color = accent, letterSpacing = 2.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("oz" to "Troy ounce", "g" to "Gram").forEach { (v, label) -> FilterChip(selected = unit == v, onClick = { onUnit(v) }, label = { Text(label) }) } }
        Text("DISPLAY CURRENCY", fontSize = 12.sp, color = accent, letterSpacing = 2.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Native", "AED").forEach { v -> FilterChip(selected = currency == v, onClick = { onCurrency(v) }, label = { Text(v) }) } }
        Text("AED display uses an approximate USD/AED peg of 3.6725. Non-USD listings remain in their native currency. This is not an executable FX quote.", fontSize = 12.sp, color = muted)
        HorizontalDivider(color = raised)
        Text("BATTERY & NETWORK", fontSize = 12.sp, color = accent, letterSpacing = 2.sp)
        Text("Refreshes approximately once per hour using Android WorkManager and only when connected. Cached prices stay visible offline. Android may postpone background work during Doze. No foreground service, continuous socket, location permission or notifications.", fontSize = 13.sp, color = muted)
        Button(onClick = { onRefresh(); back() }, enabled = key.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Save and fetch prices") }
        Text("Quotes may be delayed, exchange hours differ, and free accounts may not cover every market. This app is for personal monitoring, not trading execution.", color = muted, fontSize = 11.sp)
    }
}

private fun stamp(ts: Long): String = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(ts))
