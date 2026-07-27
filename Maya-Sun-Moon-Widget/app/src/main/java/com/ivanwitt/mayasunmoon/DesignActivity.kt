package com.ivanwitt.mayasunmoon

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

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

    private lateinit var galleryImage: ImageView
    private lateinit var galleryDefaultText: TextView
    private lateinit var galleryCounter: TextView
    private lateinit var galleryName: TextView

    private var selectedBaseColor = Color.WHITE
    private var selectedTitleColor = Color.WHITE
    private var selectedLowerPanelColor = Color.rgb(45, 45, 45)
    private var selectedDecorationStyle = DecorationStyle.DEFAULT
    private var galleryIndex = 0

    private val primaryModes = listOf(
        "Длинный счёт" to PrimaryLineMode.LONG_COUNT,
        "Григорианская дата (02 июля 1997)" to PrimaryLineMode.GREGORIAN_DATE
    )

    private val secondaryModes = listOf(
        "Цолькин + Хааб" to SecondaryLineMode.TZOLKIN_HAAB,
        "Время (00:00)" to SecondaryLineMode.TIME
    )

    private data class GalleryItem(
        val title: String,
        val style: DecorationStyle,
        val resId: Int?
    )

    private val galleryItems by lazy {
        listOf(
            GalleryItem("По умолчанию", DecorationStyle.DEFAULT, null),
            GalleryItem("Золотой храм", DecorationStyle.GOLDEN_TEMPLE, R.drawable.design_golden_temple),
            GalleryItem("Храм в джунглях", DecorationStyle.MAYA_FLIGHT, R.drawable.design_jungle_temple),
            GalleryItem("Ночная Майя", DecorationStyle.MAYA_NIGHT, R.drawable.design_maya_night_original)
        )
    }

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
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.statusBarColor = Color.argb(105, 0, 0, 0)
        window.navigationBarColor = Color.argb(105, 0, 0, 0)
        setContentView(buildUi())
        loadIntoUi()
    }

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        returnToMain()
    }

    private fun buildUi(): ScrollView {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            background = colorDrawable(Color.argb(77, 4, 28, 23), 0, false)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(34))
        }
        scroll.addView(root, ViewGroup.LayoutParams(
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
            root.setPadding(dp(18), top + dp(18), dp(18), bottom + dp(34))
            insets
        }

        root.addView(islandCard().apply {
            addView(TextView(this@DesignActivity).apply {
                text = "Дизайн"
                textSize = 30f
                setTextColor(IVORY)
                typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
            })
            addView(TextView(this@DesignActivity).apply {
                text = "✦  лёгкий островной стиль   •   Майя   •   природа  ✦"
                textSize = 13f
                setTextColor(MINT)
                setPadding(0, dp(4), 0, 0)
            })
            addView(paragraph(
                "Здесь настраивается только внешний вид виджета. " +
                    "Нулевая позиция бегунков соответствует базовому размеру."
            ))
        }, cardParams())

        root.addView(buildGalleryCard(), cardParams())

        root.addView(islandCard().apply {
            addView(section("Основной цвет", false))
            addView(paletteRow(
                label = "Линии, майянские цифры и календарные строки",
                initialColor = selectedBaseColor,
                onPreviewReady = { baseColorPreview = it },
                onSelected = { selectedBaseColor = it }
            ), fullWidth())
        }, cardParams())

        root.addView(islandCard().apply {
            addView(section("Верхняя подпись", false))
            titleInput = EditText(this@DesignActivity).apply {
                hint = "Ваш текст"
                setSingleLine(true)
                textSize = 18f
                setTextColor(IVORY)
                setHintTextColor(Color.argb(155, 255, 255, 255))
                backgroundTintList = android.content.res.ColorStateList.valueOf(SAND)
            }
            addView(titleInput, fullWidth())
            addView(paletteRow(
                label = "Цвет подписи",
                initialColor = selectedTitleColor,
                onPreviewReady = { titleColorPreview = it },
                onSelected = { selectedTitleColor = it }
            ), fullWidth())
            val titleSize = sizeControl()
            titleSizeLabel = titleSize.first
            titleSizeSeek = titleSize.second
            addView(titleSizeLabel, fullWidth())
            addView(titleSizeSeek, fullWidth())
        }, cardParams())

        root.addView(islandCard().apply {
            addView(section("Календарные строки", false))
            addView(fieldLabel("Первая строка"))
            primaryModeSpinner = themedSpinner(primaryModes.map { it.first })
            addView(primaryModeSpinner, fullWidth())
            val primarySize = sizeControl()
            primarySizeLabel = primarySize.first
            primarySizeSeek = primarySize.second
            addView(primarySizeLabel, fullWidth())
            addView(primarySizeSeek, fullWidth())

            addView(fieldLabel("Вторая строка").apply { setPadding(0, dp(14), 0, dp(6)) })
            secondaryModeSpinner = themedSpinner(secondaryModes.map { it.first })
            addView(secondaryModeSpinner, fullWidth())
            val secondarySize = sizeControl()
            secondarySizeLabel = secondarySize.first
            secondarySizeSeek = secondarySize.second
            addView(secondarySizeLabel, fullWidth())
            addView(secondarySizeSeek, fullWidth())
        }, cardParams())

        root.addView(islandCard().apply {
            addView(section("Нижняя полуокружность", false))
            addView(paletteRow(
                label = "Цвет фона",
                initialColor = selectedLowerPanelColor,
                onPreviewReady = { lowerPanelColorPreview = it },
                onSelected = { selectedLowerPanelColor = it }
            ), fullWidth())
            lowerPanelTransparencyLabel = paragraph("")
            addView(lowerPanelTransparencyLabel, fullWidth())
            lowerPanelTransparencySeek = SeekBar(this@DesignActivity).apply {
                max = 100
                progress = 50
                progressTintList = android.content.res.ColorStateList.valueOf(SAND)
                thumbTintList = android.content.res.ColorStateList.valueOf(SAND)
                setOnSeekBarChangeListener(simpleSeekListener { value ->
                    lowerPanelTransparencyLabel.text = "Прозрачность: $value%"
                })
            }
            addView(lowerPanelTransparencySeek, fullWidth())

            showLocationCheck = CheckBox(this@DesignActivity).apply {
                text = "Отображать текущий город и страну (English)"
                textSize = 15f
                setTextColor(IVORY)
                buttonTintList = android.content.res.ColorStateList.valueOf(MINT)
                setPadding(0, dp(10), 0, 0)
            }
            addView(showLocationCheck, fullWidth())
        }, cardParams())

        root.addView(airButton("СОХРАНИТЬ ДИЗАЙН") {
            saveDesignAndReturn()
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(58)
        ).apply { setMargins(0, dp(3), 0, dp(10)) })

        root.addView(ghostButton("←  НАЗАД БЕЗ СОХРАНЕНИЯ") {
            returnToMain()
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(52)
        ))

        return scroll
    }

    private fun buildGalleryCard(): LinearLayout = islandCard().apply {
        addView(section("Выбор оформления", false))
        addView(paragraph(
            "Рисунок помещается на задний план верхней полуокружности, " +
                "привязан к линии горизонта и масштабируется вместе с виджетом."
        ))

        val preview = FrameLayout(this@DesignActivity).apply {
            background = colorDrawable(
                Color.argb(72, 2, 19, 17),
                dp(20),
                true,
                Color.argb(150, 198, 231, 207)
            )
        }
        galleryImage = ImageView(this@DesignActivity).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            setPadding(dp(14), dp(18), dp(14), dp(16))
        }
        preview.addView(galleryImage, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        galleryDefaultText = TextView(this@DesignActivity).apply {
            gravity = Gravity.CENTER
            text = "По умолчанию\nбез дополнительного PNG"
            textSize = 18f
            setTextColor(SOFT_TEXT)
            typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
        }
        preview.addView(galleryDefaultText, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        galleryCounter = TextView(this@DesignActivity).apply {
            gravity = Gravity.CENTER
            textSize = 13f
            setTextColor(IVORY)
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = colorDrawable(Color.argb(145, 0, 0, 0), dp(12), false)
        }
        preview.addView(galleryCounter, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END
        ).apply { setMargins(0, dp(10), dp(10), 0) })

        galleryName = TextView(this@DesignActivity).apply {
            gravity = Gravity.CENTER_VERTICAL
            textSize = 14f
            setTextColor(MINT)
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = colorDrawable(Color.argb(105, 7, 48, 40), dp(12), false)
        }
        preview.addView(galleryName, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.START
        ).apply { setMargins(dp(10), 0, 0, dp(10)) })

        var downX = 0f
        preview.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = event.x - downX
                    if (abs(dx) >= dp(48)) {
                        moveGallery(if (dx < 0) 1 else -1)
                    }
                    true
                }
                else -> true
            }
        }
        addView(preview, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(190)
        ).apply { setMargins(0, dp(4), 0, dp(10)) })

        val controls = LinearLayout(this@DesignActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        controls.addView(galleryArrow("‹") { moveGallery(-1) }, LinearLayout.LayoutParams(dp(58), dp(54)))
        controls.addView(airButton("СОХРАНИТЬ") {
            selectedDecorationStyle = galleryItems[galleryIndex].style
            WidgetPrefs.saveDecorationStyle(this@DesignActivity, selectedDecorationStyle)
            MayaWidgetProvider.updateAll(this@DesignActivity)
            Toast.makeText(this@DesignActivity, "Оформление сохранено: ${galleryItems[galleryIndex].title}", Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(0, dp(54), 1f).apply { setMargins(dp(10), 0, dp(10), 0) })
        controls.addView(galleryArrow("›") { moveGallery(1) }, LinearLayout.LayoutParams(dp(58), dp(54)))
        addView(controls, fullWidth())
    }

    private fun moveGallery(delta: Int) {
        galleryIndex = (galleryIndex + delta + galleryItems.size) % galleryItems.size
        selectedDecorationStyle = galleryItems[galleryIndex].style
        renderGalleryPreview()
    }

    private fun renderGalleryPreview() {
        if (!::galleryImage.isInitialized) return
        val item = galleryItems[galleryIndex]
        galleryCounter.text = "${galleryIndex + 1}/${galleryItems.size}"
        galleryName.text = item.title
        if (item.resId == null) {
            galleryImage.setImageDrawable(null)
            galleryImage.visibility = View.INVISIBLE
            galleryDefaultText.visibility = View.VISIBLE
        } else {
            galleryDefaultText.visibility = View.GONE
            galleryImage.visibility = View.VISIBLE
            galleryImage.setImageResource(item.resId)
        }
    }

    private fun loadIntoUi() {
        val s = WidgetPrefs.load(this)
        selectedBaseColor = s.color
        selectedTitleColor = s.titleColor
        selectedLowerPanelColor = s.lowerPanelColor
        selectedDecorationStyle = s.decorationStyle
        galleryIndex = galleryItems.indexOfFirst { it.style == selectedDecorationStyle }.coerceAtLeast(0)

        setPreviewColor(baseColorPreview, selectedBaseColor)
        setPreviewColor(titleColorPreview, selectedTitleColor)
        setPreviewColor(lowerPanelColorPreview, selectedLowerPanelColor)
        renderGalleryPreview()

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

    private fun saveDesignAndReturn() {
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
            lowerPanelTransparencyPercent = lowerPanelTransparencySeek.progress,
            decorationStyle = galleryItems[galleryIndex].style
        )
        MayaWidgetProvider.updateAll(this)
        Toast.makeText(this, "Дизайн сохранён.", Toast.LENGTH_SHORT).show()
        returnToMain()
    }

    private fun returnToMain() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        })
        finish()
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
            setPadding(0, dp(5), 0, dp(5))
        }
        row.addView(TextView(this).apply {
            text = label
            textSize = 15f
            setTextColor(SOFT_TEXT)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val preview = TextView(this)
        setPreviewColor(preview, initialColor)
        row.addView(preview, LinearLayout.LayoutParams(dp(38), dp(38)).apply {
            setMargins(dp(8), 0, dp(9), 0)
        })
        onPreviewReady(preview)

        val paletteButton = ImageButton(this).apply {
            setImageBitmap(PaletteIcon.bitmap())
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(4), dp(4), dp(4), dp(4))
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription = "Открыть палитру"
            setOnClickListener {
                showColorPalette(label) { color ->
                    setPreviewColor(preview, color)
                    onSelected(color)
                }
            }
        }
        row.addView(paletteButton, LinearLayout.LayoutParams(dp(50), dp(50)))
        return row
    }

    private fun showColorPalette(title: String, onSelected: (Int) -> Unit) {
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(Color.rgb(10, 35, 30))
        }
        val scroller = ScrollView(this).apply { addView(list) }
        var dialog: AlertDialog? = null
        colors.forEach { choice ->
            val button = Button(this).apply {
                text = choice.label
                textSize = 14f
                setTextColor(contrastTextColor(choice.value))
                background = colorDrawable(choice.value, dp(10), true)
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
            progressTintList = android.content.res.ColorStateList.valueOf(SAND)
            thumbTintList = android.content.res.ColorStateList.valueOf(SAND)
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
        view.background = colorDrawable(color, dp(10), true)
    }

    private fun islandCard(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(17), dp(16), dp(17), dp(16))
        background = colorDrawable(
            Color.argb(112, 12, 70, 60),
            dp(22),
            true,
            Color.argb(155, 194, 232, 205)
        )
    }

    private fun section(text: String, topPadding: Boolean = true): TextView = TextView(this).apply {
        this.text = text
        textSize = 19f
        setTextColor(IVORY)
        setPadding(0, if (topPadding) dp(10) else 0, 0, dp(8))
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }

    private fun fieldLabel(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 16f
        setTextColor(MINT)
        setPadding(0, dp(2), 0, dp(6))
    }

    private fun paragraph(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(SOFT_TEXT)
        setLineSpacing(0f, 1.15f)
        setPadding(0, dp(5), 0, dp(9))
    }

    private fun themedSpinner(items: List<String>): Spinner = Spinner(this).apply {
        adapter = object : ArrayAdapter<String>(this@DesignActivity, android.R.layout.simple_spinner_dropdown_item, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                styled(super.getView(position, convertView, parent), false)
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
                styled(super.getDropDownView(position, convertView, parent), true)
            private fun styled(view: View, dropdown: Boolean): View {
                (view as? TextView)?.apply {
                    setTextColor(IVORY)
                    textSize = 15f
                    setPadding(dp(10), dp(10), dp(10), dp(10))
                    if (dropdown) setBackgroundColor(Color.rgb(14, 55, 47))
                }
                return view
            }
        }
        backgroundTintList = android.content.res.ColorStateList.valueOf(SAND)
    }

    private fun airButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 14f
        setTextColor(IVORY)
        isAllCaps = false
        background = colorDrawable(
            Color.argb(155, 25, 126, 103),
            dp(20),
            true,
            Color.argb(190, 232, 214, 161)
        )
        setOnClickListener { action() }
    }

    private fun ghostButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 13f
        setTextColor(SOFT_TEXT)
        isAllCaps = false
        background = colorDrawable(
            Color.argb(70, 6, 40, 34),
            dp(20),
            true,
            Color.argb(130, 194, 232, 205)
        )
        setOnClickListener { action() }
    }

    private fun galleryArrow(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 30f
        setTextColor(IVORY)
        isAllCaps = false
        setPadding(0, 0, 0, dp(4))
        background = colorDrawable(
            Color.argb(115, 7, 68, 57),
            dp(18),
            true,
            Color.argb(155, 194, 232, 205)
        )
        setOnClickListener { action() }
    }

    private fun colorDrawable(
        color: Int,
        radius: Int,
        addBorder: Boolean,
        borderColor: Int = Color.argb(145, 180, 180, 180)
    ): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius.toFloat()
        setColor(color)
        if (addBorder) setStroke(dp(1), borderColor)
    }

    private fun contrastTextColor(background: Int): Int {
        val luminance = 0.299 * Color.red(background) + 0.587 * Color.green(background) + 0.114 * Color.blue(background)
        return if (luminance > 150) Color.BLACK else Color.WHITE
    }

    private fun cardParams() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { setMargins(0, 0, 0, dp(13)) }

    private fun fullWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private val IVORY = Color.rgb(250, 247, 235)
        private val MINT = Color.rgb(193, 238, 216)
        private val SAND = Color.rgb(232, 214, 161)
        private val SOFT_TEXT = Color.rgb(220, 232, 225)
    }
}
