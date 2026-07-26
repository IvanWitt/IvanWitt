package com.ivanwitt.mayasunmoon

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var modeSpinner: Spinner
    private lateinit var correlationInput: EditText
    private lateinit var colorSpinner: Spinner
    private lateinit var showLocationCheck: CheckBox
    private lateinit var locationText: TextView
    private lateinit var dataText: TextView

    private lateinit var titleInput: EditText
    private lateinit var titleColorPreview: TextView
    private lateinit var titleSizeSeek: SeekBar
    private lateinit var titleSizeLabel: TextView
    private lateinit var primaryModeSpinner: Spinner
    private lateinit var primarySizeSeek: SeekBar
    private lateinit var primarySizeLabel: TextView
    private lateinit var secondaryModeSpinner: Spinner
    private lateinit var secondarySizeSeek: SeekBar
    private lateinit var secondarySizeLabel: TextView
    private lateinit var lowerPanelColorPreview: TextView
    private lateinit var lowerPanelTransparencySeek: SeekBar
    private lateinit var lowerPanelTransparencyLabel: TextView

    private var selectedTitleColor: Int = Color.WHITE
    private var selectedLowerPanelColor: Int = Color.rgb(45, 45, 45)

    private val modes = listOf(
        "Градус положения на дуге (0–180°)" to CenterMode.ARC_DEGREES,
        "Часов нахождения в видимости" to CenterMode.VISIBLE_HOURS,
        "Современный час 1–12" to CenterMode.CLOCK_12H
    )

    private val primaryModes = listOf(
        "Длинный счёт" to PrimaryLineMode.LONG_COUNT,
        "Григорианская дата (02 июля 1997)" to PrimaryLineMode.GREGORIAN_DATE
    )

    private val secondaryModes = listOf(
        "Цолькин + Хааб" to SecondaryLineMode.TZOLKIN_HAAB,
        "Время (00:00)" to SecondaryLineMode.TIME
    )

    private data class ColorChoice(val label: String, val value: Int) {
        override fun toString(): String = label
    }

    private val colors = listOf(
        ColorChoice("Белый", Color.WHITE),
        ColorChoice("Слоновая кость", Color.rgb(255, 250, 240)),
        ColorChoice("Серебряный", Color.rgb(192, 192, 192)),
        ColorChoice("Серый", Color.rgb(128, 128, 128)),
        ColorChoice("Графитовый", Color.rgb(54, 57, 63)),
        ColorChoice("Чёрный", Color.BLACK),
        ColorChoice("Красный", Color.rgb(210, 45, 45)),
        ColorChoice("Бордовый", Color.rgb(128, 0, 32)),
        ColorChoice("Винный", Color.rgb(114, 47, 55)),
        ColorChoice("Золотой", Color.rgb(212, 175, 55)),
        ColorChoice("Шампань", Color.rgb(247, 231, 206)),
        ColorChoice("Бронзовый", Color.rgb(205, 127, 50)),
        ColorChoice("Медный", Color.rgb(184, 115, 51)),
        ColorChoice("Розовое золото", Color.rgb(183, 110, 121)),
        ColorChoice("Изумрудный", Color.rgb(0, 128, 96)),
        ColorChoice("Зелёный", Color.rgb(65, 190, 105)),
        ColorChoice("Сапфировый", Color.rgb(15, 82, 186)),
        ColorChoice("Тёмно-синий", Color.rgb(20, 35, 90)),
        ColorChoice("Голубой", Color.rgb(70, 155, 235)),
        ColorChoice("Бирюзовый", Color.rgb(48, 190, 190)),
        ColorChoice("Фиолетовый", Color.rgb(112, 72, 180)),
        ColorChoice("Лиловый", Color.rgb(180, 140, 210))
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

    private fun buildUi(): ScrollView {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(22), dp(22), dp(32))
        }
        scroll.addView(
            root,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        root.addView(title("Maya Sun/Moon Widget", 26f))
        root.addView(paragraph(
            "Восходы и заходы Солнца и Луны загружаются из официального сервиса " +
                "U.S. Naval Observatory (USNO), сохраняются на телефоне и используются автономно до 72 часов. " +
                "Между восходом и заходом положение на дуге рассчитывается по часам телефона."
        ))

        root.addView(section("Что показывает число в полукруге"))
        modeSpinner = Spinner(this)
        modeSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            modes.map { it.first }
        )
        root.addView(modeSpinner, fullWidth())
        root.addView(paragraph(
            "В режиме «Часов нахождения в видимости» выводится округлённая продолжительность " +
                "интервала восход → заход текущего Солнца или Луны."
        ))

        root.addView(section("Корреляция Длинного счёта"))
        correlationInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
            hint = "584283"
            setSingleLine(true)
        }
        root.addView(correlationInput, fullWidth())
        root.addView(paragraph(
            "По умолчанию: GMT 584283. Можно ввести любое целое значение корреляции."
        ))

        // All purely visual options are grouped here as requested.
        root.addView(section("Дизайн"))
        root.addView(paragraph(
            "Нулевая позиция бегунков размера соответствует размерам текста на эталонном скриншоте. " +
                "Влево — уменьшение, вправо — увеличение."
        ))

        root.addView(designLabel("Цвет линий, майянских цифр и календарных строк"))
        colorSpinner = Spinner(this)
        colorSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            colors
        )
        root.addView(colorSpinner, fullWidth())

        root.addView(designLabel("Верхняя подпись"))
        titleInput = EditText(this).apply {
            hint = "Ваш текст"
            setSingleLine(true)
        }
        root.addView(titleInput, fullWidth())
        val titleColorRow = paletteRow(
            label = "Цвет верхней подписи",
            initialColor = selectedTitleColor,
            onPreviewReady = { titleColorPreview = it },
            onSelected = { selectedTitleColor = it }
        )
        root.addView(titleColorRow, fullWidth())
        val titleSize = sizeControl()
        titleSizeLabel = titleSize.first
        titleSizeSeek = titleSize.second
        root.addView(titleSizeLabel, fullWidth())
        root.addView(titleSizeSeek, fullWidth())

        root.addView(designLabel("Первая строка"))
        primaryModeSpinner = Spinner(this)
        primaryModeSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            primaryModes.map { it.first }
        )
        root.addView(primaryModeSpinner, fullWidth())
        val primarySize = sizeControl()
        primarySizeLabel = primarySize.first
        primarySizeSeek = primarySize.second
        root.addView(primarySizeLabel, fullWidth())
        root.addView(primarySizeSeek, fullWidth())

        root.addView(designLabel("Вторая строка"))
        secondaryModeSpinner = Spinner(this)
        secondaryModeSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            secondaryModes.map { it.first }
        )
        root.addView(secondaryModeSpinner, fullWidth())
        val secondarySize = sizeControl()
        secondarySizeLabel = secondarySize.first
        secondarySizeSeek = secondarySize.second
        root.addView(secondarySizeLabel, fullWidth())
        root.addView(secondarySizeSeek, fullWidth())

        root.addView(designLabel("Нижняя полуокружность"))
        val lowerPanelColorRow = paletteRow(
            label = "Цвет нижней полуокружности",
            initialColor = selectedLowerPanelColor,
            onPreviewReady = { lowerPanelColorPreview = it },
            onSelected = { selectedLowerPanelColor = it }
        )
        root.addView(lowerPanelColorRow, fullWidth())
        lowerPanelTransparencyLabel = TextView(this).apply {
            textSize = 15f
            setPadding(0, dp(8), 0, 0)
        }
        root.addView(lowerPanelTransparencyLabel, fullWidth())
        lowerPanelTransparencySeek = SeekBar(this).apply {
            max = 100
            progress = 50
            setOnSeekBarChangeListener(simpleSeekListener { value ->
                lowerPanelTransparencyLabel.text = "Прозрачность: $value%"
            })
        }
        root.addView(lowerPanelTransparencySeek, fullWidth())

        showLocationCheck = CheckBox(this).apply {
            text = "Отображать текущий город и страну (English)"
            setPadding(0, dp(12), 0, 0)
        }
        root.addView(showLocationCheck, fullWidth())

        root.addView(section("Местоположение"))
        locationText = paragraph("")
        root.addView(locationText)

        val updateLocation = Button(this).apply {
            text = "Обновить местоположение и данные"
            setOnClickListener { ensureLocationPermissionAndRefresh() }
        }
        root.addView(updateLocation, fullWidth())

        root.addView(section("Астрономические данные"))
        dataText = paragraph("")
        root.addView(dataText)

        val updateData = Button(this).apply {
            text = "Обновить данные сейчас"
            setOnClickListener {
                val settings = WidgetPrefs.load(this@MainActivity)
                if (!settings.hasLocationFix) {
                    Toast.makeText(
                        this@MainActivity,
                        "Сначала обновите местоположение.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    AstroSyncJobService.schedule(this@MainActivity)
                    Toast.makeText(
                        this@MainActivity,
                        "Обновление USNO запрошено. При наличии интернета данные загрузятся автоматически.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
        root.addView(updateData, fullWidth())

        val save = Button(this).apply {
            text = "Сохранить и обновить виджет"
            setOnClickListener { saveDisplaySettings() }
        }
        root.addView(save, fullWidth())

        root.addView(paragraph(
            "После успешного обновления интернет для работы виджета не нужен. Через 72 часа система " +
                "ставит обновление в очередь и выполнит его при первом доступном сетевом подключении."
        ))

        return scroll
    }

    private fun loadIntoUi() {
        val s = WidgetPrefs.load(this)
        modeSpinner.setSelection(modes.indexOfFirst { it.second == s.centerMode }.coerceAtLeast(0))
        correlationInput.setText(s.correlation.toString())
        colorSpinner.setSelection(colors.indexOfFirst { it.value == s.color }.coerceAtLeast(0))
        showLocationCheck.isChecked = s.showLocationName

        titleInput.setText(s.titleText)
        selectedTitleColor = s.titleColor
        setPreviewColor(titleColorPreview, selectedTitleColor)
        setSizeControl(titleSizeSeek, titleSizeLabel, s.titleSizeOffsetPercent)

        primaryModeSpinner.setSelection(
            primaryModes.indexOfFirst { it.second == s.primaryLineMode }.coerceAtLeast(0)
        )
        setSizeControl(primarySizeSeek, primarySizeLabel, s.primarySizeOffsetPercent)

        secondaryModeSpinner.setSelection(
            secondaryModes.indexOfFirst { it.second == s.secondaryLineMode }.coerceAtLeast(0)
        )
        setSizeControl(secondarySizeSeek, secondarySizeLabel, s.secondarySizeOffsetPercent)

        selectedLowerPanelColor = s.lowerPanelColor
        setPreviewColor(lowerPanelColorPreview, selectedLowerPanelColor)
        lowerPanelTransparencySeek.progress = s.lowerPanelTransparencyPercent
        lowerPanelTransparencyLabel.text = "Прозрачность: ${s.lowerPanelTransparencyPercent}%"

        renderLocation(s)
        renderDataStatus(s)
    }

    private fun saveDisplaySettings() {
        val mode = modes[modeSpinner.selectedItemPosition].second
        val correlation = correlationInput.text.toString().trim().toIntOrNull()
        if (correlation == null) {
            Toast.makeText(this, "Введите целое число корреляции.", Toast.LENGTH_LONG).show()
            return
        }

        val color = colors[colorSpinner.selectedItemPosition].value
        WidgetPrefs.saveDisplay(
            this,
            mode,
            correlation,
            color,
            showLocationCheck.isChecked
        )
        WidgetPrefs.saveDesign(
            context = this,
            titleText = titleInput.text.toString().trim().ifBlank { "Ваш текст" },
            titleColor = selectedTitleColor,
            titleSizeOffsetPercent = sizeOffset(titleSizeSeek),
            primaryLineMode = primaryModes[primaryModeSpinner.selectedItemPosition].second,
            primarySizeOffsetPercent = sizeOffset(primarySizeSeek),
            secondaryLineMode = secondaryModes[secondaryModeSpinner.selectedItemPosition].second,
            secondarySizeOffsetPercent = sizeOffset(secondarySizeSeek),
            lowerPanelColor = selectedLowerPanelColor,
            lowerPanelTransparencyPercent = lowerPanelTransparencySeek.progress
        )
        MayaWidgetProvider.updateAll(this)
        Toast.makeText(this, "Настройки сохранены.", Toast.LENGTH_SHORT).show()
    }

    private fun paletteRow(
        label: String,
        initialColor: Int,
        onPreviewReady: (TextView) -> Unit,
        onSelected: (Int) -> Unit
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
        }
        val text = TextView(this).apply {
            this.text = label
            textSize = 15f
        }
        row.addView(text, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val preview = TextView(this).apply {
            contentDescription = "Выбранный цвет"
        }
        setPreviewColor(preview, initialColor)
        row.addView(preview, LinearLayout.LayoutParams(dp(36), dp(36)).apply {
            marginEnd = dp(8)
        })
        onPreviewReady(preview)

        val palette = Button(this).apply {
            text = "🎨"
            textSize = 20f
            contentDescription = "Открыть палитру"
            setOnClickListener {
                showColorPalette(label) { color ->
                    setPreviewColor(preview, color)
                    onSelected(color)
                }
            }
        }
        row.addView(palette, LinearLayout.LayoutParams(dp(64), dp(48)))
        return row
    }

    private fun showColorPalette(title: String, onSelected: (Int) -> Unit) {
        val grid = GridLayout(this).apply {
            columnCount = 4
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        lateinit var dialog: AlertDialog
        colors.forEach { choice ->
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(4), dp(6), dp(4), dp(6))
            }
            val swatch = TextView(this).apply {
                contentDescription = choice.label
                background = colorDrawable(choice.value, dp(10), true)
                setOnClickListener {
                    onSelected(choice.value)
                    dialog.dismiss()
                }
            }
            cell.addView(swatch, LinearLayout.LayoutParams(dp(48), dp(48)))
            cell.addView(TextView(this).apply {
                text = choice.label
                textSize = 11f
                gravity = Gravity.CENTER
                maxLines = 2
            }, LinearLayout.LayoutParams(dp(76), ViewGroup.LayoutParams.WRAP_CONTENT))
            grid.addView(cell, GridLayout.LayoutParams().apply {
                width = dp(82)
                height = ViewGroup.LayoutParams.WRAP_CONTENT
            })
        }
        dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(grid)
            .setNegativeButton("Отмена", null)
            .create()
        dialog.show()
    }

    private fun sizeControl(): Pair<TextView, SeekBar> {
        val label = TextView(this).apply {
            text = "Размер: 0%"
            textSize = 14f
            setPadding(0, dp(6), 0, 0)
        }
        val seek = SeekBar(this).apply {
            max = 100
            progress = 50
            setOnSeekBarChangeListener(simpleSeekListener { value ->
                val offset = value - 50
                label.text = "Размер: ${if (offset > 0) "+" else ""}$offset%"
            })
        }
        return label to seek
    }

    private fun setSizeControl(seek: SeekBar, label: TextView, offset: Int) {
        val safe = offset.coerceIn(-50, 50)
        seek.progress = safe + 50
        label.text = "Размер: ${if (safe > 0) "+" else ""}$safe%"
    }

    private fun sizeOffset(seek: SeekBar): Int = (seek.progress - 50).coerceIn(-50, 50)

    private fun simpleSeekListener(onChanged: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            onChanged(progress)
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
        override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
    }

    private fun setPreviewColor(view: TextView, color: Int) {
        view.background = colorDrawable(color, dp(8), false)
    }

    private fun colorDrawable(color: Int, radius: Int, addBorder: Boolean): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius.toFloat()
            setColor(color)
            if (addBorder) setStroke(dp(1), Color.argb(120, 128, 128, 128))
        }

    private fun ensureLocationPermissionAndRefresh() {
        val fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
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
                    if (location != null) saveLocation(location)
                    else useLastKnownOrReport(manager, provider)
                }
            } else {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        saveLocation(location)
                    }
                    override fun onProviderEnabled(provider: String) = Unit
                    override fun onProviderDisabled(provider: String) = Unit
                    @Deprecated("Deprecated in Android")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                }
                manager.requestSingleUpdate(provider, listener, mainLooper)
            }
        } catch (security: SecurityException) {
            Toast.makeText(this, "Нет разрешения на местоположение.", Toast.LENGTH_LONG).show()
        }
    }

    private fun useLastKnownOrReport(manager: LocationManager, provider: String) {
        try {
            val last = manager.getLastKnownLocation(provider)
            if (last != null) saveLocation(last)
            else {
                locationText.text = "Координаты пока не получены."
                Toast.makeText(
                    this,
                    "Не удалось получить координаты. Попробуйте ещё раз на открытом месте.",
                    Toast.LENGTH_LONG
                ).show()
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
        Toast.makeText(
            this,
            "Местоположение сохранено. Данные USNO обновятся при доступном интернете.",
            Toast.LENGTH_LONG
        ).show()
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

            val city = address?.locality
                ?: address?.subAdminArea
                ?: address?.adminArea
                ?: ""
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
        val base = String.format(
            Locale.US,
            "%.5f, %.5f",
            s.latitude,
            s.longitude
        )
        locationText.text = if (s.hasLocationFix) {
            val stamp = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(Date(s.locationUpdatedAt))
            val place = listOf(s.cityName, s.countryName)
                .filter { it.isNotBlank() }
                .joinToString(", ")
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
        setPadding(0, 0, 0, dp(12))
    }

    private fun section(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 18f
        setPadding(0, dp(20), 0, dp(6))
    }

    private fun designLabel(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 16f
        setPadding(0, dp(14), 0, dp(4))
    }

    private fun paragraph(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 15f
        setLineSpacing(0f, 1.15f)
        setPadding(0, dp(4), 0, dp(8))
    }

    private fun fullWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_LOCATION = 3201
    }
}
