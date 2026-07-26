package com.ivanwitt.mayasunmoon

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var modeSpinner: Spinner
    private lateinit var correlationInput: EditText
    private lateinit var locationText: TextView
    private lateinit var dataText: TextView

    private val modes = listOf(
        "Продолжительность светового дня/ночи (ч.)" to CenterMode.VISIBLE_HOURS,
        "Современный час 1–12" to CenterMode.CLOCK_12H
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureWindow()
        setContentView(buildUi())
        loadIntoUi()
        AstroSyncJobService.scheduleIfNeeded(this)
    }

    override fun onResume() {
        super.onResume()
        if (::locationText.isInitialized) {
            val settings = WidgetPrefs.load(this)
            renderLocation(settings)
            renderDataStatus(settings)
        }
    }

    private fun configureWindow() {
        window.statusBarColor = Color.argb(220, 0, 0, 0)
        window.navigationBarColor = Color.argb(220, 0, 0, 0)
        window.setBackgroundDrawableResource(android.R.color.transparent)
    }

    private fun buildUi(): ScrollView {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.argb(218, 0, 0, 0))
            setOnApplyWindowInsetsListener { view, insets ->
                val top = if (Build.VERSION.SDK_INT >= 30) {
                    insets.getInsets(WindowInsets.Type.statusBars()).top
                } else {
                    @Suppress("DEPRECATION")
                    insets.systemWindowInsetTop
                }
                val bottom = if (Build.VERSION.SDK_INT >= 30) {
                    insets.getInsets(WindowInsets.Type.navigationBars()).bottom
                } else {
                    @Suppress("DEPRECATION")
                    insets.systemWindowInsetBottom
                }
                view.setPadding(0, top + dp(12), 0, bottom)
                insets
            }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(10), dp(22), dp(32))
        }
        scroll.addView(root, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        root.addView(title("Maya Sun/Moon Widget", 26f))
        root.addView(paragraph(
            "Восходы и заходы Солнца и Луны загружаются из официального сервиса " +
                "U.S. Naval Observatory (USNO), сохраняются на телефоне и используются автономно до 72 часов. " +
                "Положение Солнца, Луны и майянское число обновляются каждую минуту."
        ))

        root.addView(section("Что показывает число в полукруге"))
        modeSpinner = Spinner(this)
        modeSpinner.adapter = darkAdapter(modes.map { it.first })
        root.addView(modeSpinner, fullWidth())
        root.addView(paragraph(
            "В режиме «Продолжительность светового дня/ночи (ч.)» днём показывается интервал " +
                "восход → заход Солнца, а после захода — интервал заход → следующий восход."
        ))

        root.addView(section("Корреляция Длинного счёта"))
        correlationInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
            hint = "584283"
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.argb(145, 255, 255, 255))
        }
        root.addView(correlationInput, fullWidth())
        root.addView(paragraph("По умолчанию: GMT 584283. Можно ввести любое целое значение корреляции."))

        root.addView(section("Дизайн"))
        root.addView(paragraph("Все настройки внешнего вида вынесены на отдельную страницу."))
        root.addView(Button(this).apply {
            text = "Открыть настройки дизайна"
            setTextColor(Color.WHITE)
            setOnClickListener { startActivity(Intent(this@MainActivity, DesignActivity::class.java)) }
        }, fullWidth())

        root.addView(section("Местоположение"))
        locationText = paragraph("")
        root.addView(locationText)
        root.addView(Button(this).apply {
            text = "Обновить местоположение и данные"
            setTextColor(Color.WHITE)
            setOnClickListener { ensureLocationPermissionAndRefresh() }
        }, fullWidth())

        root.addView(section("Астрономические данные"))
        dataText = paragraph("")
        root.addView(dataText)
        root.addView(Button(this).apply {
            text = "Обновить данные сейчас"
            setTextColor(Color.WHITE)
            setOnClickListener {
                val settings = WidgetPrefs.load(this@MainActivity)
                if (!settings.hasLocationFix) {
                    Toast.makeText(this@MainActivity, "Сначала обновите местоположение.", Toast.LENGTH_LONG).show()
                } else {
                    AstroSyncJobService.schedule(this@MainActivity)
                    Toast.makeText(
                        this@MainActivity,
                        "Обновление USNO запрошено. При наличии интернета данные загрузятся автоматически.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }, fullWidth())

        root.addView(Button(this).apply {
            text = "Сохранить и обновить виджет"
            setTextColor(Color.WHITE)
            setOnClickListener { saveCoreSettings() }
        }, fullWidth())

        root.addView(paragraph(
            "После успешного обновления интернет для работы виджета не нужен. Через 72 часа система " +
                "ставит обновление данных в очередь и выполнит его при первом доступном сетевом подключении."
        ))
        return scroll
    }

    private fun loadIntoUi() {
        val s = WidgetPrefs.load(this)
        modeSpinner.setSelection(modes.indexOfFirst { it.second == s.centerMode }.coerceAtLeast(0))
        correlationInput.setText(s.correlation.toString())
        renderLocation(s)
        renderDataStatus(s)
    }

    private fun saveCoreSettings() {
        val correlation = correlationInput.text.toString().trim().toIntOrNull()
        if (correlation == null) {
            Toast.makeText(this, "Введите целое число корреляции.", Toast.LENGTH_LONG).show()
            return
        }
        val current = WidgetPrefs.load(this)
        WidgetPrefs.saveDisplay(
            context = this,
            mode = modes[modeSpinner.selectedItemPosition].second,
            correlation = correlation,
            color = current.color,
            showLocationName = current.showLocationName
        )
        MayaWidgetProvider.updateAll(this)
        Toast.makeText(this, "Настройки сохранены.", Toast.LENGTH_SHORT).show()
    }

    private fun ensureLocationPermissionAndRefresh() {
        val fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                REQUEST_LOCATION
            )
            return
        }
        refreshLocation()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
            refreshLocation()
        }
    }

    @Suppress("DEPRECATION")
    private fun refreshLocation() {
        val manager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val provider = when {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        }
        if (provider == null) {
            Toast.makeText(this, "Включите геолокацию на телефоне.", Toast.LENGTH_LONG).show()
            return
        }

        locationText.text = "Получаю актуальные координаты…"
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                manager.getCurrentLocation(provider, null, mainExecutor) { location ->
                    if (location != null) saveLocation(location) else useLastKnownOrReport(manager, provider)
                }
            } else {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) { saveLocation(location) }
                    override fun onProviderEnabled(provider: String) = Unit
                    override fun onProviderDisabled(provider: String) = Unit
                    @Deprecated("Deprecated in Android")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                }
                manager.requestSingleUpdate(provider, listener, mainLooper)
            }
        } catch (_: SecurityException) {
            Toast.makeText(this, "Нет разрешения на местоположение.", Toast.LENGTH_LONG).show()
        }
    }

    private fun useLastKnownOrReport(manager: LocationManager, provider: String) {
        try {
            val last = manager.getLastKnownLocation(provider)
            if (last != null) saveLocation(last) else {
                locationText.text = "Координаты пока не получены."
                Toast.makeText(this, "Не удалось получить координаты. Попробуйте ещё раз на открытом месте.", Toast.LENGTH_LONG).show()
            }
        } catch (_: SecurityException) {
            locationText.text = "Нет доступа к местоположению."
        }
    }

    private fun saveLocation(location: Location) {
        WidgetPrefs.saveLocation(
            this,
            location.latitude,
            location.longitude,
            if (location.hasAltitude()) location.altitude else 0.0
        )
        SkyScheduleStore.clear(this)
        resolveEnglishPlaceName(location)
        AstroSyncJobService.schedule(this)
        val s = WidgetPrefs.load(this)
        renderLocation(s)
        renderDataStatus(s)
        MayaWidgetProvider.updateAll(this)
        Toast.makeText(this, "Местоположение сохранено. Данные USNO обновятся при доступном интернете.", Toast.LENGTH_LONG).show()
    }

    @Suppress("DEPRECATION")
    private fun resolveEnglishPlaceName(location: Location) {
        Thread {
            val address = runCatching {
                if (!Geocoder.isPresent()) return@runCatching null
                Geocoder(this, Locale.ENGLISH)
                    .getFromLocation(location.latitude, location.longitude, 1)
                    ?.firstOrNull()
            }.getOrNull()
            val city = address?.locality ?: address?.subAdminArea ?: address?.adminArea ?: ""
            val country = address?.countryName ?: ""
            if (city.isNotBlank() || country.isNotBlank()) {
                WidgetPrefs.saveLocationNames(this, city, country)
                runOnUiThread {
                    val s = WidgetPrefs.load(this)
                    renderLocation(s)
                    MayaWidgetProvider.updateAll(this)
                }
            }
        }.start()
    }

    private fun renderLocation(s: WidgetSettings) {
        val base = String.format(Locale.US, "%.5f, %.5f", s.latitude, s.longitude)
        locationText.text = if (s.hasLocationFix) {
            val stamp = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(Date(s.locationUpdatedAt))
            val place = listOf(s.cityName, s.countryName).filter { it.isNotBlank() }.joinToString(", ")
            buildString {
                if (place.isNotBlank()) append("$place\n")
                append("Сохранённые координаты: $base\nОбновлено: $stamp")
            }
        } else {
            "Координаты ещё не обновлялись. Временный стартовый ориентир: Москва ($base)."
        }
    }

    private fun renderDataStatus(s: WidgetSettings) {
        val cache = SkyScheduleStore.load(this)
        val zone = java.time.ZoneId.systemDefault()
        dataText.text = if (cache != null && cache.matches(s, zone)) {
            val fetched = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(Date(cache.fetchedAtMillis))
            val valid = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(Date(cache.validUntilMillis))
            "Источник: U.S. Naval Observatory (USNO)\nПолучено: $fetched\nАвтономно действительно до: $valid"
        } else {
            "Источник: U.S. Naval Observatory (USNO)\nДанные для текущих координат ещё не загружены."
        }
    }

    private fun title(text: String, sp: Float): TextView = TextView(this).apply {
        this.text = text
        textSize = sp
        setTextColor(Color.WHITE)
        setPadding(0, 0, 0, dp(12))
    }

    private fun section(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 18f
        setTextColor(Color.rgb(210, 210, 210))
        setPadding(0, dp(20), 0, dp(6))
    }

    private fun paragraph(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 15f
        setTextColor(Color.rgb(190, 190, 190))
        setLineSpacing(0f, 1.15f)
        setPadding(0, dp(4), 0, dp(8))
    }

    private fun darkAdapter(items: List<String>): ArrayAdapter<String> =
        object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent)
                (v as? TextView)?.setTextColor(Color.WHITE)
                return v
            }
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getDropDownView(position, convertView, parent)
                (v as? TextView)?.apply {
                    setTextColor(Color.WHITE)
                    setBackgroundColor(Color.rgb(28, 28, 28))
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                }
                return v
            }
        }.apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

    private fun fullWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_LOCATION = 3201
    }
}
