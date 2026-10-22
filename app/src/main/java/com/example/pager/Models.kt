package com.example.pager

import androidx.compose.ui.graphics.Color

enum class KB { NONE, MULTITAP, ALPHA, QWERTY, NUMERIC }
enum class Feature { NONE, LED_COLOR, KB_BACKLIGHT, LCD_BACKLIGHT }
enum class LedMode { SIMPLE, PICKER, COUNT }
enum class Layout { STANDARD, WIDE, PAPER }

data class Model(
    val id: String, val name: String, val brand: String, val tagline: String, val price: Int,
    val bodyTop: Color, val bodyBottom: Color, val edgeLight: Color, val edgeDark: Color, val brandColor: Color,
    val lcdTop: Color, val lcdBottom: Color, val ink: Color,
    val litTop: Color = lcdTop, val litBottom: Color = lcdBottom, val litInk: Color = ink,
    val keyTop: Color, val keyBottom: Color, val keyText: Color, val glow: Color = Color(0xFF5FD4FF),
    val kb: KB, val feature: Feature, val led: LedMode, val wheel: Boolean, val layout: Layout,
    val corner: Int, val perks: List<String>
)

val LED_COLORS = listOf(Color(0xFFFF3B30), Color(0xFF34C759), Color(0xFFFFB300), Color(0xFF2979FF), Color(0xFFE040FB))
val LED_NAMES = listOf("RED", "GREEN", "AMBER", "BLUE", "MAGENTA")

/** 1-2 msgs green, 3-5 yellow, 6-10 orange, 11+ red */
fun countColor(n: Int): Color = when {
    n <= 2 -> Color(0xFF34C759)
    n <= 5 -> Color(0xFFFFE000)
    n <= 10 -> Color(0xFFFF8A00)
    else -> Color(0xFFFF2D20)
}

val MODELS = listOf(
    Model(id = "cmp1", name = "CM P1", brand = "CM", tagline = "Landscape QWERTY, scroll roller, green-tint LCD.", price = 0,
        bodyTop = Color(0xFF2E3A52), bodyBottom = Color(0xFF0C0F16), edgeLight = Color(0xFF8A93A3), edgeDark = Color(0xFF05070A), brandColor = Color(0xFFB5BDCB),
        lcdTop = Color(0xFF9CA68C), lcdBottom = Color(0xFF8A957B), ink = Color(0xFF1F2A19),
        litTop = Color(0xFFC9EBA3), litBottom = Color(0xFFB3D88B), litInk = Color(0xFF16240F),
        keyTop = Color(0xFF4A5366), keyBottom = Color(0xFF1B212E), keyText = Color(0xFFC3CAD6), glow = Color(0xFF9BE564),
        kb = KB.QWERTY, feature = Feature.LCD_BACKLIGHT, led = LedMode.COUNT, wheel = true, layout = Layout.WIDE, corner = 16,
        perks = listOf("Full QWERTY, made for landscape", "Roller scrolls images", "LED: green to red by message count", "Green LCD backlight", "ROTATE button on the body")),
    Model(id = "pagertron", name = "Pagertron 3000", brand = "PAGERTRON 3000", tagline = "The simplest pager. Multitap keypad.", price = 1,
        bodyTop = Color(0xFF3A3E45), bodyBottom = Color(0xFF1A1C20), edgeLight = Color(0xFF6A707A), edgeDark = Color(0xFF0E0F11), brandColor = Color(0xFF9AA0AA),
        lcdTop = Color(0xFFA3B58F), lcdBottom = Color(0xFF8DA179), ink = Color(0xFF1E2A1A),
        keyTop = Color(0xFF5B6068), keyBottom = Color(0xFF2F3237), keyText = Color(0xFFD8DCE2),
        kb = KB.MULTITAP, feature = Feature.NONE, led = LedMode.SIMPLE, wheel = false, layout = Layout.STANDARD, corner = 32,
        perks = listOf("Arrow keys scroll images")),
    Model(id = "doc", name = "Doc Beeper D1", brand = "DOC BEEPER", tagline = "Numeric keypad, hospital white.", price = 2,
        bodyTop = Color(0xFFF1EEE4), bodyBottom = Color(0xFFCFC9B8), edgeLight = Color(0xFFFFFFFF), edgeDark = Color(0xFF8F8A7B), brandColor = Color(0xFF555148),
        lcdTop = Color(0xFFC5CAB5), lcdBottom = Color(0xFFB3B9A3), ink = Color(0xFF232820),
        litTop = Color(0xFFFFD166), litBottom = Color(0xFFF2A93B), litInk = Color(0xFF3A2400),
        keyTop = Color(0xFFF4F1E8), keyBottom = Color(0xFFBDB7A6), keyText = Color(0xFF2E2C27),
        kb = KB.NUMERIC, feature = Feature.LCD_BACKLIGHT, led = LedMode.SIMPLE, wheel = false, layout = Layout.STANDARD, corner = 22,
        perks = listOf("Amber LCD backlight", "Arrow keys scroll images")),
    Model(id = "motor", name = "Motor Roller Advizor", brand = "MOTOR ROLLER", tagline = "A-Z grid keys. Pick your LED colour.", price = 4,
        bodyTop = Color(0xFF2B2B2B), bodyBottom = Color(0xFF080808), edgeLight = Color(0xFF777777), edgeDark = Color(0xFF000000), brandColor = Color(0xFFD4AF37),
        lcdTop = Color(0xFFB8C4A4), lcdBottom = Color(0xFFA5B390), ink = Color(0xFF1A2214),
        keyTop = Color(0xFF3E3E3E), keyBottom = Color(0xFF151515), keyText = Color(0xFFE8B04A),
        kb = KB.ALPHA, feature = Feature.LED_COLOR, led = LedMode.PICKER, wheel = true, layout = Layout.STANDARD, corner = 18,
        perks = listOf("Changeable LED colour", "Scroll wheel for images")),
    Model(id = "blueberry", name = "Blueberry 850", brand = "BLUEBERRY", tagline = "Chiclet QWERTY with an illuminated keyboard.", price = 6,
        bodyTop = Color(0xFF22304D), bodyBottom = Color(0xFF0A111F), edgeLight = Color(0xFF9FB0CC), edgeDark = Color(0xFF05080F), brandColor = Color(0xFFBFD0EA),
        lcdTop = Color(0xFFCBD8E8), lcdBottom = Color(0xFFB2C3D8), ink = Color(0xFF0F1A2B),
        keyTop = Color(0xFF3A4660), keyBottom = Color(0xFF151C2B), keyText = Color(0xFF8FA3C4), glow = Color(0xFF5FD4FF),
        kb = KB.QWERTY, feature = Feature.KB_BACKLIGHT, led = LedMode.SIMPLE, wheel = true, layout = Layout.STANDARD, corner = 20,
        perks = listOf("Illuminated keyboard", "Scroll wheel for images")),
    Model(id = "paper", name = "CM Paper", brand = "CM PAPER", tagline = "Touch e-ink pad. Draw and send 128x256 images.", price = 8,
        bodyTop = Color(0xFF5B6270), bodyBottom = Color(0xFF262B34), edgeLight = Color(0xFFA9B0BD), edgeDark = Color(0xFF0B0D11), brandColor = Color(0xFFD5DAE3),
        lcdTop = Color(0xFFE6E4D6), lcdBottom = Color(0xFFE6E4D6), ink = Color(0xFF14140E),
        keyTop = Color(0xFF6A7282), keyBottom = Color(0xFF2B313C), keyText = Color(0xFFE2E6EE),
        kb = KB.NONE, feature = Feature.NONE, led = LedMode.COUNT, wheel = false, layout = Layout.PAPER, corner = 26,
        perks = listOf("Resistive touchscreen, buttons only", "128x256 e-ink, sends images", "Stylus compartment", "LED: green to red by message count"))
)
