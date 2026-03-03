package com.echo.echocalendar.ui.demo

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.echo.echocalendar.BuildConfig
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class SyncedHolidayKind {
    PUBLIC_HOLIDAY,
    COMMEMORATIVE
}

data class SyncedHoliday(
    val date: LocalDate,
    val label: String,
    val kind: SyncedHolidayKind
)

object HolidaySyncStore {
    private const val TAG = "HolidaySyncStore"
    private const val PREFS_NAME = "holiday_sync"
    private const val KEY_BODY = "body"
    private const val KEY_LAST_CHECKED_AT = "last_checked_at"
    private const val FULL_SYNC_START_DATE = "1970-01-01"
    private const val FULL_SYNC_END_DATE = "2080-12-31"

    private data class FetchResult(
        val startDate: LocalDate,
        val endDate: LocalDate,
        val holidays: List<SyncedHoliday>
    )

    suspend fun loadCachedOnly(context: Context): List<SyncedHoliday> = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadCached(prefs)
    }

    suspend fun refreshAndLoad(context: Context): List<SyncedHoliday> = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cached = loadCached(prefs)
        if (!isInternetAvailable(context)) {
            return@withContext cached
        }
        val fetched = fetchFromRemote()
        if (fetched == null) return@withContext cached

        val merged = mergeHolidays(
            cached = cached,
            fetched = fetched.holidays,
            fetchedStartDate = fetched.startDate,
            fetchedEndDate = fetched.endDate
        )
        saveCached(prefs, merged)
        merged
    }

    private fun fetchFromRemote(): FetchResult? {
        val syncUrl = BuildConfig.HOLIDAY_SYNC_URL.trim()
        if (syncUrl.isBlank()) return null

        val startDate = LocalDate.parse(FULL_SYNC_START_DATE)
        val endDate = LocalDate.parse(FULL_SYNC_END_DATE)
        val requestUrl = buildRangeUrl(syncUrl, startDate, endDate)

        val connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = BuildConfig.HOLIDAY_SYNC_TIMEOUT_MS
            readTimeout = BuildConfig.HOLIDAY_SYNC_TIMEOUT_MS
            doInput = true
        }

        try {
            when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val parsed = parseBody(body)
                    Log.i(
                        TAG,
                        "holiday_sync_success mode=launch_full range=${startDate}..${endDate} count=${parsed.size}"
                    )
                    return FetchResult(
                        startDate = startDate,
                        endDate = endDate,
                        holidays = parsed
                    )
                }
                else -> {
                    Log.w(TAG, "holiday_sync_http_error code=$code")
                }
            }
        } catch (error: Exception) {
            Log.w(TAG, "holiday_sync_failed reason=${error.message}")
        } finally {
            connection.disconnect()
        }
        return null
    }

    private fun isInternetAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun buildRangeUrl(baseUrl: String, startDate: LocalDate, endDate: LocalDate): String {
        val delimiter = if ('?' in baseUrl) "&" else "?"
        val start = URLEncoder.encode(startDate.toString(), Charsets.UTF_8.name())
        val end = URLEncoder.encode(endDate.toString(), Charsets.UTF_8.name())
        return "$baseUrl${delimiter}startDate=$start&endDate=$end"
    }

    private fun mergeHolidays(
        cached: List<SyncedHoliday>,
        fetched: List<SyncedHoliday>,
        fetchedStartDate: LocalDate,
        fetchedEndDate: LocalDate
    ): List<SyncedHoliday> {
        val byDateAndKind = linkedMapOf<Pair<LocalDate, SyncedHolidayKind>, SyncedHoliday>()
        cached.filterNot { item ->
            !item.date.isBefore(fetchedStartDate) && !item.date.isAfter(fetchedEndDate)
        }.forEach { item ->
            byDateAndKind[item.date to item.kind] = item
        }
        fetched.forEach { item ->
            byDateAndKind[item.date to item.kind] = item
        }
        return byDateAndKind.values.sortedWith(
            compareBy<SyncedHoliday> { it.date }
                .thenBy { it.kind.name }
                .thenBy { it.label }
        )
    }

    private fun loadCached(prefs: SharedPreferences): List<SyncedHoliday> {
        val body = prefs.getString(KEY_BODY, null).orEmpty()
        return parseBody(body)
    }

    private fun saveCached(prefs: SharedPreferences, holidays: List<SyncedHoliday>) {
        val array = JSONArray()
        holidays.forEach { item ->
            array.put(
                JSONObject()
                    .put("date", item.date.toString())
                    .put("label", item.label)
                    .put("kind", item.kind.name)
            )
        }
        val body = JSONObject().put("holidays", array).toString()
        prefs.edit()
            .putString(KEY_BODY, body)
            .putLong(KEY_LAST_CHECKED_AT, System.currentTimeMillis())
            .apply()
    }

    private fun parseBody(body: String): List<SyncedHoliday> {
        if (body.isBlank()) return emptyList()
        return runCatching {
            val root = JSONObject(body)
            val array = root.optJSONArray("holidays") ?: return emptyList()
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val dateText = item.optString("date").trim()
                    val label = item.optString("label").trim()
                    val kindText = item.optString("kind", "PUBLIC_HOLIDAY").trim().uppercase()
                    if (dateText.isBlank() || label.isBlank()) continue
                    val date = runCatching { LocalDate.parse(dateText) }.getOrNull() ?: continue
                    val kind = when (kindText) {
                        "PUBLIC_HOLIDAY" -> SyncedHolidayKind.PUBLIC_HOLIDAY
                        "COMMEMORATIVE" -> SyncedHolidayKind.COMMEMORATIVE
                        else -> continue
                    }
                    add(SyncedHoliday(date = date, label = label, kind = kind))
                }
            }.distinctBy { it.date to it.kind to it.label }
        }.getOrElse {
            emptyList()
        }
    }
}
