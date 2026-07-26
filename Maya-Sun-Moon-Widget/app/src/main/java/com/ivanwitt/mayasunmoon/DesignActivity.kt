package com.ivanwitt.mayasunmoon

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
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
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

class DesignActivity : Activity() {
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
    private lateinit var showLocationCheck: CheckBox
    private lateinit var baseColorPreview: TextView

    private var selectedBaseColor = Color.WHITE
    private var selectedTitleColor = Color.WHITE
    private var selectedLowerPanelColor = Color.rgb(45, 45, 45)

    private val primaryModes = listOf(
        "Длинный счёт" to PrimaryLineMode.LONG_COUNT,
        "Григорианская дата (02 июля 1997)" to PrimaryLineMode.GREGORIAN_DATE
    )

    private val secondaryModes = listOf(
        "Цолькин + Хааб" to SecondaryLineMode.TZOLKIN_HAAB,
        "Время (00:00)" to SecondaryLineMode.TIME
    )

    private data class ColorChoice(val label: String, val value: Int)

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
        window.statusBarColor = Color.argb(220, 0, 0, 0)
        window.navigationBarColor = Color.argb(220, 0, 0, 0)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        setContentView(buildUi())
        loadIntoUi()
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

        root.addView(title("Дизайн", 28f))
        root.addView(paragraph(
            "Нулевая позиция каждого бегунка соответствует базовому размеру. " +
                "Влево — уменьшение, вправо — увеличение."
        ))

        root.addView(section("Основной цвет"))
        root.addView(paletteRow(
            label = "Линии, майянские цифры и календарные строки",
            initialColor = selectedBaseColor,
            onPreviewReady = { baseColorPreview = it },
            onSelected = { selectedBaseColor = it }
        ), fullWidth())

        root.addView(section("Верхняя подпись"))
        titleInput = EditText(this).apply {
            hint = "Ваш текст"
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.argb(145, 255, 255, 255))
        }
        root.addView(titleInput, fullWidth())
        root.addView(paletteRow(
            label = "Цвет подписи",
            initialColor = selectedTitleColor,
            onPreviewReady = { titleColorPreview = it },
            onSelected = { selectedTitleColor = it }
        ), fullWidth())
        val titleSize = sizeControl()
        titleSizeLabel = titleSize.first
        titleSizeSeek = titleSize.second
        root.addView(titleSizeLabel, fullWidth())
        root.addView(titleSizeSeek, fullWidth())

        root.addView(section("Первая строка"))
        primaryModeSpinner = Spinner(this)
        primaryModeSpinner.adapter = darkAdapter(primaryModes.map { it.first })
        root.addView(primaryModeSpinner, fullWidth())
        val primarySize = sizeControl()
        primarySizeLabel = primarySize.first
        primarySizeSeek = primarySize.second
        root.addView(primarySizeLabel, fullWidth())
        root.addView(primarySizeSeek, fullWidth())

        root.addView(section("Вторая строка"))
        secondaryModeSpinner = Spinner(this)
        secondaryModeSpinner.adapter = darkAdapter(secondaryModes.map { it.first })
        root.addView(secondaryModeSpinner, fullWidth())
        val secondarySize = sizeControl()
        secondarySizeLabel = secondarySize.first
        secondarySizeSeek = secondarySize.second
        root.addView(secondarySizeLabel, fullWidth())
        root.addView(secondarySizeSeek, fullWidth())

        root.addView(section("Нижняя полуокружность"))
        root.addView(paletteRow(
            label = "Цвет фона",
            initialColor = selectedLowerPanelColor,
            onPreviewReady = { lowerPanelColorPreview = it },
            onSelected = { selectedLowerPanelColor = it }
        ), fullWidth())
        lowerPanelTransparencyLabel = paragraph("")
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
            setTextColor(Color.WHITE)
            setPadding(0, dp(14), 0, dp(8))
        }
        root.addView(showLocationCheck, fullWidth())

        root.addView(Button(this).apply {
            text = "Сохранить дизайн"
            setTextColor(Color.WHITE)
            setOnClickListener { saveDesign() }
        }, fullWidth())

        root.addView(Button(this).apply {
            text = "Назад"
            setTextColor(Color.WHITE)
            setOnClickListener { finish() }
        }, fullWidth())

        return scroll
    }

    private fun loadIntoUi() {
        val s = WidgetPrefs.load(this)
        selectedBaseColor = s.color
        selectedTitleColor = s.titleColor
        selectedLowerPanelColor = s.lowerPanelColor
        setPreviewColor(baseColorPreview, selectedBaseColor)
        setPreviewColor(titleColorPreview, selectedTitleColor)
        setPreviewColor(lowerPanelColorPreview, selectedLowerPanelColor)

        titleInput.setText(s.titleText)
        setSizeControl(titleSizeSeek, titleSizeLabel, s.titleSizeOffsetPercent)
        primaryModeSpinner.setSelection(primaryModes.indexOfFirst { it.second == s.primaryLineMode }.coerceAtLeast(0))
        setSizeControl(primarySizeSeek, primarySizeLabel, s.primarySizeOffsetPercent)
        secondaryModeSpinner.setSelection(secondaryModes.indexOfFirst { it.second == s.secondaryLineMode }.coerceAtLeast(0))
        setSizeControl(secondarySizeSeek, secondarySizeLabel, s.secondarySizeOffsetPercent)
        lowerPanelTransparencySeek.progress = s.lowerPanelTransparencyPercent
        lowerPanelTransparencyLabel.text = "Прозрачность: ${s.lowerPanelTransparencyPercent}%"
        showLocationCheck.isChecked = s.showLocationName
    }

    private fun saveDesign() {
        val current = WidgetPrefs.load(this)
        WidgetPrefs.saveDisplay(
            context = this,
            mode = current.centerMode,
            correlation = current.correlation,
            color = selectedBaseColor,
            showLocationName = showLocationCheck.isChecked
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
        Toast.makeText(this, "Дизайн сохранён.", Toast.LENGTH_SHORT).show()
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
        row.addView(TextView(this).apply {
            text = label
            textSize = 15f
            setTextColor(Color.rgb(215, 215, 215))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val preview = TextView(this)
        setPreviewColor(preview, initialColor)
        row.addView(preview, LinearLayout.LayoutParams(dp(34), dp(34)).apply {
            setMargins(0, 0, dp(10), 0)
        })
        onPreviewReady(preview)

        val paletteButton = ImageButton(this).apply {
            setImageBitmap(PaletteIcon.bitmap())
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(5), dp(5), dp(5), dp(5))
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = "Открыть палитру"
            setOnClickListener {
                showColorPalette(label) { color ->
                    setPreviewColor(preview, color)
                    onSelected(color)
                }
            }
        }
        row.addView(paletteButton, LinearLayout.LayoutParams(dp(52), dp(52)))
        return row
    }

    private fun showColorPalette(title: String, onSelected: (Int) -> Unit) {
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(Color.rgb(18, 18, 18))
        }
        val scroller = ScrollView(this).apply { addView(list) }
        var dialog: AlertDialog? = null
        colors.forEach { choice ->
            val button = Button(this).apply {
                text = choice.label
                textSize = 14f
                setTextColor(contrastTextColor(choice.value))
                background = colorDrawable(choice.value, dp(8), true)
                setOnClickListener {
                    onSelected(choice.value)
                    dialog?.dismiss()
                }
            }
            list.addView(button, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply {
                setMargins(0, dp(3), 0, dp(3))
            })
        }
        dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(scroller)
            .setNegativeButton("Отмена", null)
            .create()
        dialog?.show()
    }

    private fun sizeControl(): Pair<TextView, SeekBar> {
        val label = paragraph("Размер: 0%")
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

    private fun simpleSeekListener(onChanged: (Int) -> Unit): SeekBar.OnSeekBarChangeListener =
        object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = onChanged(progress)
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        }

    private fun setPreviewColor(view: TextView, color: Int) {
        view.background = colorDrawable(color, dp(8), true)
    }

    private fun colorDrawable(color: Int, radius: Int, addBorder: Boolean): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius.toFloat()
            setColor(color)
            if (addBorder) setStroke(dp(1), Color.argb(150, 180, 180, 180))
        }

    private fun contrastTextColor(background: Int): Int {
        val luminance = 0.299 * Color.red(background) + 0.587 * Color.green(background) + 0.114 * Color.blue(background)
        return if (luminance > 150) Color.BLACK else Color.WHITE
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

    private fun title(text: String, sp: Float): TextView = TextView(this).apply {
        this.text = text
        textSize = sp
        setTextColor(Color.WHITE)
        setPadding(0, 0, 0, dp(12))
    }

    private fun section(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 18f
        setTextColor(Color.rgb(220, 220, 220))
        setPadding(0, dp(20), 0, dp(6))
    }

    private fun paragraph(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 15f
        setTextColor(Color.rgb(190, 190, 190))
        setLineSpacing(0f, 1.15f)
        setPadding(0, dp(4), 0, dp(8))
    }

    private fun fullWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
