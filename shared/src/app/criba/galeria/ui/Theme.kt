package app.criba.galeria.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val CribaLight = lightColorScheme(
    primary = Color(0xFF1F4FA8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE5F8),
    onPrimaryContainer = Color(0xFF0B2459),
    secondary = Color(0xFF52607A),
    tertiary = Color(0xFFB7791F),
    background = Color(0xFFF7F8FA),
    surface = Color(0xFFF7F8FA),
    surfaceVariant = Color(0xFFE2E6EC),
    surfaceContainer = Color(0xFFEDF0F4),
    error = Color(0xFFB3261E),
)

val CribaDark = darkColorScheme(
    primary = Color(0xFFAFC6FF),
    onPrimary = Color(0xFF0B2459),
    primaryContainer = Color(0xFF233F7A),
    onPrimaryContainer = Color(0xFFDCE5F8),
    secondary = Color(0xFFB9C6E0),
    tertiary = Color(0xFFF0C27A),
    background = Color(0xFF0F1114),
    surface = Color(0xFF0F1114),
    surfaceVariant = Color(0xFF262A30),
    surfaceContainer = Color(0xFF181B1F),
    error = Color(0xFFF2B8B5),
)

private val ES: Locale = Locale.forLanguageTag("es")
private val DAY = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", ES)
private val MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", ES)

fun localDate(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

fun dayLabel(d: LocalDate): String {
    val today = LocalDate.now()
    return when (d) {
        today -> "Hoy"
        today.minusDays(1) -> "Ayer"
        else -> DAY.format(d).replaceFirstChar { it.titlecase(ES) }
    }
}

fun monthLabel(ms: Long): String = MONTH.format(localDate(ms)).replaceFirstChar { it.titlecase(ES) }

fun fullDate(ms: Long): String =
    DateTimeFormatter.ofPattern("d 'de' MMMM yyyy · HH:mm", ES).format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
