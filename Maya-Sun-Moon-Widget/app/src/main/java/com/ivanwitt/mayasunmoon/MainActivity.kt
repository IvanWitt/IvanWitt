package com.ivanwitt.mayasunmoon

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
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
    private val modes = listOf(
        "Продолжительность светового дня/ночи (ч.)" to CenterMode.DAY_NIGHT_DURATION,
        "Современный час (1–12)" to CenterMode.CLOCK_12H
    )

    private lateinit var mainRoot: LinearLayout
    private lateinit var modeSpinner: Spinner
    private lateinit var correlationInput: EditText
    private lateinit var locationText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        setContentView(buildContent())
        loadIntoUi()
    }

    override fun onResume() {
        super.onResume()
        if (::mainRoot.isInitialized) {
            mainRoot.visibility = View.VISIBLE
            loadIntoUi()
        }
    }

    private fun buildContent(): ScrollView {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            background = colorDrawable(Color.argb(77, 9, 28, 24), 0f, 0)
        }
        mainRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(30))
        }
        scroll.addView(mainRoot, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        scroll.setOnApplyWindowInsetsListener { _, insets ->
            val top = if (Build.VERSION.SDK_INT >= 30) {
                insets.getInsets(WindowInsets.Type.statusBars()).top
            } else {
                @Suppress("DEPRECATION") insets.systemWindowInsetTop
            }
            val bottom = if (Build.VERSION.SDK_INT >= 30) {
                insets.getInsets(WindowInsets.Type.navigationBars()).bottom
            } else {
                @Suppress("DEPRECATION") insets.systemWindowInsetBottom
            }
            mainRoot.setPadding(dp(18), top + dp(18), dp(18), bottom + dp(30))
            insets
        }

        val header = glassCard().apply {
            addView(TextView(this@MainActivity).apply {
                text = "Maya Sun/Moon"
                textSize = 29f
                setTextColor(IVORY)
                typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
            })
            addView(TextView(this@MainActivity).apply {
                text = "☼   остров времени   ✦   календарь Майя   ☾"
                textSize = 13f
                setTextColor(MINT)
                setPadding(0, dp(4), 0, 0)
            })
            addView(TextView(this@MainActivity).apply {
                text = "Солнце, Луна и календарные циклы работают автономно по сохранённым данным."
                textSize = 14f
                setTextColor(SOFT_TEXT)
                setPadding(0, dp(10), 0, 0)
            })
        }
        mainRoot.addView(header, matchCardParams())

        val displayCard = glassCard()
        displayCard.addView(sectionTitle("Что показывает число в полукруге"))
        modeSpinner = themedSpinner(modes.map { it.first })
        displayCard.addView(modeSpinner, fieldParams())
        displayCard.addView(TextView(this).apply {
            text = "Днём — длительность от восхода до захода Солнца. После захода — длительность ночи от предыдущего захода до следующего восхода."
            textSize = 13f
            setTextColor(SOFT_TEXT)
            setPadding(0, dp(8), 0, 0)
        })
        mainRoot.addView(displayCard, matchCardParams())

        val calendarCard = glassCard()
        calendarCard.addView(sectionTitle("Корреляция Длинного счёта"))
        correlationInput = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
            textSize = 18f
            setTextColor(IVORY)
            setHintTextColor(Color.argb(150, 255, 255, 255))
            backgroundTintList = android.content.res.ColorStateList.valueOf(SAND)
            setSingleLine(true)
        }
        calendarCard.addView(correlationInput, fieldParams())
        calendarCard.addView(TextView(this).apply {
            text = "По умолчанию GMT 584283."
            textSize = 13f
            setTextColor(SOFT_TEXT)
            setPadding(0, dp(6), 0, 0)
        })
        mainRoot.addView(calendarCard, matchCardParams())

        val designCard = glassCard()
        designCard.addView(sectionTitle("Дизайн"))
        designCard.addView(TextView(this).apply {
            text = "Оформление, декоративные PNG, цвета, подписи, размеры строк и нижняя полусфера."
            textSize = 14f
            setTextColor(SOFT_TEXT)
            setPadding(0, 0, 0, dp(10))
        })
        designCard.addView(airButton("ОТКРЫТЬ ДИЗАЙН  →") {
            // Both activities are translucent. Hide this page before opening DesignActivity,
            // so the previous settings page never shows through beneath it.
            mainRoot.visibility = View.INVISIBLE
            startActivity(Intent(this, DesignActivity::class.java))
        }, fieldParams())
        mainRoot.addView(designCard, matchCardParams())

        val locationCard = glassCard()
        locationCard.addView(sectionTitle("Местоположение"))
        locationText = TextView(this).apply {
            textSize = 14f
            setTextColor(IVORY)
            setPadding(0, 0, 0, dp(10))
        }
        locationCard.addView(locationText)
        locationCard.addView(airButton("ОБНОВИТЬ МЕСТОПОЛОЖЕНИЕ") {
            ensureLocationPermissionAndRefresh()
        }, fieldParams())
        locationCard.addView(TextView(this).apply {
            text = "Восходы и заходы сохраняются на телефоне и используются автономно до 72 часов."
            textSize = 13f
            setTextColor(SOFT_TEXT)
            setPadding(0, dp(10), 0, 0)
        })
        mainRoot.addView(locationCard, matchCardParams())

        mainRoot.addView(airButton("СОХРАНИТЬ И ОБНОВИТЬ ВИДЖЕТ") {
            saveDisplaySettings()
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(54)
        ).apply { setMargins(0, dp(8), 0, 0) })

        return scroll
    }

    private fun loadIntoUi() {
        if (!::modeSpinner.isInitialized) return
        val s = WidgetPrefs.load(this)
        modeSpinner.setSelection(modes.indexOfFirst { it.second == s.centerMode }.coerceAtLeast(0))
        correlationInput.setText(s.correlation.toString())
        renderLocation(s)
    }

    private fun saveDisplaySettings() {
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
            requestPermissions(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ), REQUEST_LOCATION)
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
                    if (location != null) saveLocation(location)
                    else useLastKnownOrReport(manager, provider)
                }
            } else {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) = saveLocation(location)
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
            if (last != null) saveLocation(last)
            else {
                locationText.text = "Координаты пока не получены."
                Toast.makeText(this, "Не удалось получить координаты. Попробуйте ещё раз.", Toast.LENGTH_LONG).show()
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
        renderLocation(WidgetPrefs.load(this))
        MayaWidgetProvider.updateAll(this)
        Toast.makeText(this, "Местоположение сохранено.", Toast.LENGTH_SHORT).show()
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
                    renderLocation(WidgetPrefs.load(this))
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
                append("$base  •  обновлено $stamp")
            }
        } else {
            "Координаты ещё не обновлялись. Временный ориентир: Москва ($base)."
        }
    }

    private fun glassCard(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(15), dp(16), dp(15))
        background = colorDrawable(Color.argb(78, 20, 72, 62), dp(20).toFloat(), Color.argb(115, 209, 235, 205))
    }

    private fun sectionTitle(textValue: String) = TextView(this).apply {
        text = textValue
        textSize = 18f
        setTextColor(IVORY)
        setPadding(0, 0, 0, dp(9))
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }

    private fun airButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 13f
        setTextColor(IVORY)
        isAllCaps = false
        background = colorDrawable(Color.argb(105, 27, 115, 98), dp(18).toFloat(), Color.argb(150, 233, 220, 165))
        setOnClickListener { action() }
    }

    private fun themedSpinner(values: List<String>): Spinner = Spinner(this).apply {
        adapter = object : ArrayAdapter<String>(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, values) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                styled(super.getView(position, convertView, parent), false)
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
                styled(super.getDropDownView(position, convertView, parent), true)
            private fun styled(view: View, dropdown: Boolean): View {
                (view as? TextView)?.apply {
                    setTextColor(IVORY)
                    textSize = 15f
                    setPadding(dp(10), dp(10), dp(10), dp(10))
                    if (dropdown) setBackgroundColor(Color.rgb(18, 55, 48))
                }
                return view
            }
        }
        backgroundTintList = android.content.res.ColorStateList.valueOf(SAND)
    }

    private fun colorDrawable(color: Int, radius: Float, strokeColor: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(color)
            if (strokeColor != 0) setStroke(dp(1), strokeColor)
        }

    private fun matchCardParams() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { setMargins(0, 0, 0, dp(12)) }

    private fun fieldParams() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_LOCATION = 1207
        private val IVORY = Color.rgb(250, 247, 235)
        private val MINT = Color.rgb(190, 232, 210)
        private val SAND = Color.rgb(232, 214, 161)
        private val SOFT_TEXT = Color.rgb(216, 226, 219)
    }
}
