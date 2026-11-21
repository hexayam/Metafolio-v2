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


private val assetCatalog = listOf(
    Asset("XAU/USD", "Gold spot", "Metals"),
    Asset("XAG/USD", "Silver spot", "Metals"),
    Asset("XPT/USD", "Platinum spot", "Metals"),
    Asset("XPD/USD", "Palladium spot", "Metals"),
    Asset("VOO", "Vanguard S&P 500", "ETFs"),
    Asset("QQQ", "Invesco Nasdaq-100", "ETFs"),
    Asset("SPY", "SPDR S&P 500", "ETFs"),
    Asset("IVV", "iShares Core S&P 500", "ETFs"),
    Asset("VTI", "Vanguard Total Stock Market", "ETFs"),
    Asset("VT", "Vanguard Total World", "ETFs"),
    Asset("SCHD", "Schwab US Dividend Equity", "ETFs"),
    Asset("IWM", "iShares Russell 2000", "ETFs"),
    Asset("GLD", "SPDR Gold Shares", "ETFs"),
    Asset("SLV", "iShares Silver Trust", "ETFs"),
    Asset("VWRA", "Vanguard FTSE All-World (LSE)", "ETFs"),
    Asset("CSPX", "iShares Core S&P 500 (LSE)", "ETFs"),
    Asset("VUAA", "Vanguard S&P 500 UCITS (LSE)", "ETFs"),
    Asset("AAPL", "Apple", "Stocks"),
    Asset("MSFT", "Microsoft", "Stocks"),
    Asset("NVDA", "NVIDIA", "Stocks"),
    Asset("GOOGL", "Alphabet", "Stocks"),
    Asset("AMZN", "Amazon", "Stocks"),
    Asset("META", "Meta Platforms", "Stocks"),
    Asset("TSLA", "Tesla", "Stocks"),
    Asset("AMD", "AMD", "Stocks"),
    Asset("AVGO", "Broadcom", "Stocks"),
    Asset("BRK.B", "Berkshire Hathaway B", "Stocks"),
    Asset("JPM", "JPMorgan Chase", "Stocks"),
    Asset("V", "Visa", "Stocks"),
    Asset("ASML", "ASML", "Stocks"),
    Asset("TSM", "Taiwan Semiconductor", "Stocks"),
    Asset("NVO", "Novo Nordisk", "Stocks")
)

@Composable private fun WatchlistScreen(assets: List<Asset>, back: () -> Unit, onUpdate: (List<Asset>) -> Unit) {
    var search by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("All") }
    var customOpen by remember { mutableStateOf(false) }
    var symbol by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var quoteCurrency by remember { mutableStateOf("USD") }
    var customType by remember { mutableStateOf("Stocks") }
    var typeMenu by remember { mutableStateOf(false) }

    val results = assetCatalog.filter { candidate ->
        (group == "All" || candidate.type == group) &&
        (search.isBlank() || candidate.symbol.contains(search, ignoreCase = true) ||
            candidate.label.contains(search, ignoreCase = true))
    }

    Header("Manage assets", back)
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = search,
            onValueChange = { search = it.take(70) },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (search.isNotBlank()) {
                    IconButton(onClick = { search = "" }) { Icon(Icons.Default.Close, "Clear search") }
                }
            },
            label = { Text("Search name or ticker") },
            placeholder = { Text("Gold, Apple, VOO...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
        )
        CategorySelector(group, listOf("All", "Metals", "ETFs", "Stocks")) { group = it }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            item {
                Text(
                    "DISCOVER • ${results.size} MATCHES",
                    color = muted,
                    fontSize = 11.sp,
                    letterSpacing = 1.4.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            items(results, key = { "catalog_${it.symbol}" }) { candidate ->
                val added = assets.any { it.symbol == candidate.symbol }
                Surface(
                    color = surface,
                    shape = RoundedCornerShape(16.dp),
                    onClick = {
                        if (!added) onUpdate(assets + candidate)
                        else onUpdate(assets.filterNot { it.symbol == candidate.symbol })
                    }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(candidate.symbol, color = ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(candidate.label, color = muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (added) {
                            Icon(Icons.Default.CheckCircle, "Added; tap to remove", tint = good)
                        } else {
                            Icon(Icons.Default.AddCircleOutline, "Add ${candidate.symbol}", tint = accent)
                        }
                    }
                }
            }
            if (results.isEmpty()) {
                item {
                    Text(
                        "No preset matches. Use Add custom ticker below for any other supported market symbol.",
                        color = muted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 14.dp)
                    )
                }
            }

            item {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = raised)
                TextButton(
                    onClick = {
                        customOpen = !customOpen
                        if (customOpen && symbol.isBlank()) {
                            symbol = search.trim().uppercase(Locale.US).take(30)
                            customType = if (group == "All") "Stocks" else group
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(if (customOpen) Icons.Default.ExpandLess else Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (customOpen) "Hide custom ticker" else "Add custom ticker", color = accent)
                }
            }
            if (customOpen) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box {
                            OutlinedButton(
                                onClick = { typeMenu = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Category: $customType")
                                Spacer(Modifier.weight(1f))
                                Icon(Icons.Default.ArrowDropDown, null)
                            }
                            DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                                listOf("Metals", "ETFs", "Stocks").forEach { type ->
                                    DropdownMenuItem(
                                        text = { Text(type) },
                                        onClick = { customType = type; typeMenu = false }
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = symbol,
                            onValueChange = { symbol = it.trim().uppercase(Locale.US).take(30) },
                            label = { Text("Ticker symbol") },
                            placeholder = { Text("e.g. VUAA or XAU/USD") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it.take(60) },
                            label = { Text("Display name (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = quoteCurrency,
                            onValueChange = { quoteCurrency = it.trim().uppercase(Locale.US).take(6) },
                            label = { Text("Quote currency") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                val ticker = symbol.trim()
                                if (ticker.isNotBlank() && assets.none { it.symbol == ticker }) {
                                    onUpdate(
                                        assets + Asset(
                                            ticker,
                                            name.ifBlank { ticker },
                                            customType,
                                            quoteCurrency.ifBlank { "USD" }
                                        )
                                    )
                                    symbol = ""
                                    name = ""
                                    customOpen = false
                                    search = ""
                                }
                            },
                            enabled = symbol.isNotBlank() && assets.none { it.symbol == symbol },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("Add to watchlist") }
                        Text(
                            "Presets and custom symbols depend on your Twelve Data subscription. " +
                                "For international listings, check the provider's exact symbol and quote currency.",
                            color = muted,
                            fontSize = 11.sp
                        )
                    }
                }
            }
            item {
                Spacer(Modifier.height(12.dp))
                Text("YOUR WATCHLIST • ${assets.size} ASSETS", color = muted, fontSize = 11.sp, letterSpacing = 1.3.sp)
            }
            items(assets, key = { "owned_${it.symbol}" }) { a ->
                Surface(color = surface, shape = RoundedCornerShape(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(a.symbol, color = ink, fontWeight = FontWeight.SemiBold)
                            Text("${a.label} · ${a.type}", color = muted, fontSize = 11.sp)
                        }
                        IconButton(onClick = { onUpdate(assets.filterNot { it.symbol == a.symbol }) }) {
                            Icon(Icons.Default.Close, "Remove ${a.symbol}", tint = muted)
                        }
                    }
                }
            }
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
