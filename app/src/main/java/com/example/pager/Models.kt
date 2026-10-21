package com.example.pager

import androidx.compose.ui.graphics.Color

enum class KB { MULTITAP, ALPHA, QWERTY, NUMERIC }
enum class Feature { NONE, LED_COLOR, KB_BACKLIGHT, LCD_BACKLIGHT }

data class Model(
    val id: String, val name: String, val brand: String, val tagline: String, val price: Int,
    val bodyTop: Color, val bodyBottom: Color, val edgeLight: Color, val edgeDark: Color, val brandColor: Color,
    val lcdTop: Color, val lcdBottom: Color, val ink: Color,
    val keyTop: Color, val keyBottom: Color, val keyText: Color,
    val kb: KB, val feature: Feature, val corner: Int
)

val LED_COLORS = listOf(Color(0xFFFF3B30), Color(0xFF34C759), Color(0xFFFFB300), Color(0xFF2979FF), Color(0xFFE040FB))
val LED_NAMES = listOf("RED", "GREEN", "AMBER", "BLUE", "MAGENTA")

val MODELS = listOf(
    Model("pagertron", "Pagertron 3000", "PAGERTRON 3000", "Phone-style multitap keypad. The classic.", 0,
        Color(0xFF3A3E45), Color(0xFF1A1C20), Color(0xFF6A707A), Color(0xFF0E0F11), Color(0xFF9AA0AA),
        Color(0xFFA3B58F), Color(0xFF8DA179), Color(0xFF1E2A1A),
        Color(0xFF5B6068), Color(0xFF2F3237), Color(0xFFD8DCE2), KB.MULTITAP, Feature.NONE, 32),
    Model("doc", "Doc Beeper D1", "DOC BEEPER", "Numeric keypad, hospital white. Amber LCD backlight.", 2,
        Color(0xFFF1EEE4), Color(0xFFCFC9B8), Color(0xFFFFFFFF), Color(0xFF8F8A7B), Color(0xFF555148),
        Color(0xFFC5CAB5), Color(0xFFB3B9A3), Color(0xFF232820),
        Color(0xFFF4F1E8), Color(0xFFBDB7A6), Color(0xFF2E2C27), KB.NUMERIC, Feature.LCD_BACKLIGHT, 22),
    Model("motor", "Motor Roller Advizor", "MOTOR ROLLER", "A-Z grid keys. Pick your LED colour.", 3,
        Color(0xFF2B2B2B), Color(0xFF080808), Color(0xFF777777), Color(0xFF000000), Color(0xFFD4AF37),
        Color(0xFFB8C4A4), Color(0xFFA5B390), Color(0xFF1A2214),
        Color(0xFF3E3E3E), Color(0xFF151515), Color(0xFFE8B04A), KB.ALPHA, Feature.LED_COLOR, 18),
    Model("blueberry", "Blueberry 850", "BLUEBERRY", "Chiclet QWERTY with an illuminated keyboard.", 6,
        Color(0xFF22304D), Color(0xFF0A111F), Color(0xFF9FB0CC), Color(0xFF05080F), Color(0xFFBFD0EA),
        Color(0xFFCBD8E8), Color(0xFFB2C3D8), Color(0xFF0F1A2B),
        Color(0xFF3A4660), Color(0xFF151C2B), Color(0xFF8FA3C4), KB.QWERTY, Feature.KB_BACKLIGHT, 20)
)
