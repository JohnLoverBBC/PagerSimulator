package com.example.pager

import androidx.compose.ui.graphics.Color

/** WIDE_QWERTY = full-size landscape board (CM P1). PAPER = no keyboard, resistive touchscreen (CM Paper Pager). */
enum class KB { MULTITAP, ALPHA, QWERTY, NUMERIC, WIDE_QWERTY, PAPER }
enum class Feature { NONE, LED_COLOR, KB_BACKLIGHT, LCD_BACKLIGHT }

data class Model(
    val id: String, val name: String, val brand: String, val tagline: String, val price: Int,
    val bodyTop: Color, val bodyBottom: Color, val edgeLight: Color, val edgeDark: Color, val brandColor: Color,
    val lcdTop: Color, val lcdBottom: Color, val ink: Color,
    val keyTop: Color, val keyBottom: Color, val keyText: Color,
    val kb: KB, val feature: Feature, val corner: Int,
    val perks: String = "No extras",
    /** scroll wheel / roller beside the screen (used to scroll received images) */
    val wheel: Boolean = false,
    /** LED colour follows the message count: green -> yellow -> orange -> red */
    val countLed: Boolean = false,
    /** built to be held sideways (wide body) */
    val landscapeNative: Boolean = false,
    val eink: Boolean = false,
    /** LCD colours while the backlight is on */
    val litTop: Color = Color(0xFFFFD166), val litBottom: Color = Color(0xFFF2A93B), val litInk: Color = Color(0xFF3A2400),
    val litName: String = "Amber"
)

val LED_COLORS = listOf(Color(0xFFFF3B30), Color(0xFF34C759), Color(0xFFFFB300), Color(0xFF2979FF), Color(0xFFE040FB))
val LED_NAMES = listOf("RED", "GREEN", "AMBER", "BLUE", "MAGENTA")

/** 0 = dark, 1-2 green, 3-5 yellow, 6-10 orange, 11+ red. */
fun countLedColor(n: Int): Color = when {
    n <= 0 -> Color(0xFF3A1010)
    n <= 2 -> Color(0xFF34C759)
    n <= 5 -> Color(0xFFFFD60A)
    n <= 10 -> Color(0xFFFF8A00)
    else -> Color(0xFFFF3B30)
}

const val DEFAULT_MODEL = "cmqwerty"

val MODELS = listOf(
    // cheapest + simplest + the default
    Model("cmqwerty", "CM Qwerty", "CM QWERTY", "The default. Plain QWERTY, nothing fancy.", 0,
        Color(0xFF6B737D), Color(0xFF3F454D), Color(0xFF9BA3AD), Color(0xFF1C1F23), Color(0xFFD8DCE2),
        Color(0xFFB4C09E), Color(0xFF9FAD89), Color(0xFF1E2A1A),
        Color(0xFF7C848E), Color(0xFF474D55), Color(0xFFF1F3F6), KB.QWERTY, Feature.NONE, 26,
        perks = "Simplest pager. No extras."),
    Model("pagertron", "Pagertron 3000", "PAGERTRON 3000", "Phone-style multitap keypad. The classic.", 1,
        Color(0xFF3A3E45), Color(0xFF1A1C20), Color(0xFF6A707A), Color(0xFF0E0F11), Color(0xFF9AA0AA),
        Color(0xFFA3B58F), Color(0xFF8DA179), Color(0xFF1E2A1A),
        Color(0xFF5B6068), Color(0xFF2F3237), Color(0xFFD8DCE2), KB.MULTITAP, Feature.NONE, 32),
    Model("doc", "Doc Beeper D1", "DOC BEEPER", "Numeric keypad, hospital white. Amber LCD backlight.", 2,
        Color(0xFFF1EEE4), Color(0xFFCFC9B8), Color(0xFFFFFFFF), Color(0xFF8F8A7B), Color(0xFF555148),
        Color(0xFFC5CAB5), Color(0xFFB3B9A3), Color(0xFF232820),
        Color(0xFFF4F1E8), Color(0xFFBDB7A6), Color(0xFF2E2C27), KB.NUMERIC, Feature.LCD_BACKLIGHT, 22,
        perks = "★ Amber LCD backlight"),
    Model("motor", "Motor Roller Advizor", "MOTOR ROLLER", "A-Z grid keys. Pick your LED colour.", 3,
        Color(0xFF2B2B2B), Color(0xFF080808), Color(0xFF777777), Color(0xFF000000), Color(0xFFD4AF37),
        Color(0xFFB8C4A4), Color(0xFFA5B390), Color(0xFF1A2214),
        Color(0xFF3E3E3E), Color(0xFF151515), Color(0xFFE8B04A), KB.ALPHA, Feature.LED_COLOR, 18,
        perks = "★ Changeable LED colour  ★ Scroll roller", wheel = true),
    Model("blueberry", "Blueberry 850", "BLUEBERRY", "Chiclet QWERTY with an illuminated keyboard.", 6,
        Color(0xFF22304D), Color(0xFF0A111F), Color(0xFF9FB0CC), Color(0xFF05080F), Color(0xFFBFD0EA),
        Color(0xFFCBD8E8), Color(0xFFB2C3D8), Color(0xFF0F1A2B),
        Color(0xFF3A4660), Color(0xFF151C2B), Color(0xFF8FA3C4), KB.QWERTY, Feature.KB_BACKLIGHT, 20,
        perks = "★ Illuminated keyboard  ★ Scroll wheel", wheel = true),
    Model("cmpaper", "CM Paper Pager", "CM PAPER", "Resistive touchscreen + 128×256 e-ink. Draw a note, send it as an image.", 8,
        Color(0xFFEDEBE4), Color(0xFFC9C6BB), Color(0xFFFFFFFF), Color(0xFF8F8B7E), Color(0xFF4A4A46),
        Color(0xFFDAD8CD), Color(0xFFCFCDC1), Color(0xFF15151A),
        Color(0xFF45484D), Color(0xFF1B1C1F), Color(0xFFE4E6EA), KB.PAPER, Feature.NONE, 24,
        perks = "★ E-ink 128×256  ★ Stylus  ★ Count LED", countLed = true, eink = true),
    Model("cmp1", "CM P1", "CM", "Sideways QWERTY, scroll roller, Nokia-green backlight and a message-count LED.", 10,
        Color(0xFF1F2D4A), Color(0xFF0A101F), Color(0xFF5A647C), Color(0xFF05070C), Color(0xFFAAB5CA),
        Color(0xFFA7ABA3), Color(0xFF969A92), Color(0xFF22251F),
        Color(0xFF4C5059), Color(0xFF22252B), Color(0xFFE4E7EC), KB.WIDE_QWERTY, Feature.LCD_BACKLIGHT, 16,
        perks = "★ Landscape QWERTY  ★ Roller  ★ Count LED  ★ Green backlight",
        wheel = true, countLed = true, landscapeNative = true,
        litTop = Color(0xFFC8E88A), litBottom = Color(0xFF9FCB5E), litInk = Color(0xFF1B2E0A), litName = "Green")
)
