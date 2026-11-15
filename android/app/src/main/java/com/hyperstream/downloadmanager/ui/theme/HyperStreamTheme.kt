package com.hyperstream.downloadmanager.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class MdmThemeStyle(val id: String, val title: String) {
    CLASSIC_IDM("classic", "Classic MDM / IDM Cobalt"),
    METRO_DARK("metro", "Metro Midnight OLED"),
    MATERIAL_DYNAMIC("material", "Material 3 HyperStream Emerald");

    companion object {
        fun fromId(id: String): MdmThemeStyle {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: CLASSIC_IDM
        }
    }
}

private val ClassicDarkScheme = darkColorScheme(
    primary = Color(0xFF4FC3F7),
    onPrimary = Color(0xFF002538),
    primaryContainer = Color(0xFF004B6F),
    onPrimaryContainer = Color(0xFFC7E7FF),
    secondary = Color(0xFF81C784),
    tertiary = Color(0xFFFFB74D),
    background = Color(0xFF0E141B),
    surface = Color(0xFF151E28),
    surfaceVariant = Color(0xFF1E2A38)
)

private val MetroDarkScheme = darkColorScheme(
    primary = Color(0xFF00E5FF),
    onPrimary = Color(0xFF002024),
    primaryContainer = Color(0xFF004F58),
    onPrimaryContainer = Color(0xFF97F0FF),
    secondary = Color(0xFF7C4DFF),
    tertiary = Color(0xFFFF4081),
    background = Color(0xFF06080C),
    surface = Color(0xFF0D1117),
    surfaceVariant = Color(0xFF161B22)
)

private val MaterialEmeraldScheme = darkColorScheme(
    primary = Color(0xFF69F0AE),
    onPrimary = Color(0xFF00391D),
    primaryContainer = Color(0xFF00522C),
    onPrimaryContainer = Color(0xFF86F8C6),
    secondary = Color(0xFF80D8FF),
    tertiary = Color(0xFFFFD54F),
    background = Color(0xFF0B1210),
    surface = Color(0xFF131D1A),
    surfaceVariant = Color(0xFF1C2A25)
)

private val ClassicLightScheme = lightColorScheme(
    primary = Color(0xFF0277BD),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB3E5FC),
    onPrimaryContainer = Color(0xFF001F2A),
    secondary = Color(0xFF2E7D32),
    tertiary = Color(0xFFEF6C00)
)

@Composable
fun HyperStreamTheme(
    themeId: String = "classic",
    darkModePref: String = "dark",
    content: @Composable () -> Unit
) {
    val style = MdmThemeStyle.fromId(themeId)
    val useDark = when (darkModePref.lowercase()) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val colors = if (!useDark) {
        ClassicLightScheme
    } else {
        when (style) {
            MdmThemeStyle.CLASSIC_IDM -> ClassicDarkScheme
            MdmThemeStyle.METRO_DARK -> MetroDarkScheme
            MdmThemeStyle.MATERIAL_DYNAMIC -> MaterialEmeraldScheme
        }
    }
    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}
