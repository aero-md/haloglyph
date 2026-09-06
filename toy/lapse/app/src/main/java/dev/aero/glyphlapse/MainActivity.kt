package dev.aero.glyphlapse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aero.glyphlapse.engine.LapseEngine
import dev.aero.glyphlapse.engine.TimeBreakdown
import dev.aero.glyphlapse.render.LapseRenderer
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Interface de configuration : date/heure de référence, style d'affichage,
 * animation des secondes — persistés en SharedPreferences (le toy écoute) —
 * plus une préview live 25×25 partageant moteur et renderer avec le toy.
 * Style « cartes » (fond noir, légendes serif, valeurs monospace, rouge Nothing).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ConfigScreen() }
    }
}

private val BG = Color(0xFF0A0A0B)
private val CARD = Color(0xFF161619)
private val CARD_LINE = Color(0x12FFFFFF)
private val TXT = Color(0xFFF1F1EF)
private val MUTED = Color(0xFF9A9AA0)
private val LABEL = Color(0xFFE9E9E7)
private val ACCENT = Color(0xFFD71921)
private val YELLOW = Color(0xFFFFC700) // brand yellow Nothing (playground.nothing.tech)
private val GREY_BTN = Color(0xFF2B2B30)
private val SEL_LINE = Color(0x33FFFFFF)
private val MENU_BG = Color(0xFF1D1D21)

private val FORMAT_LABELS = mapOf(
    LapseEngine.Format.DETAIL2 to "Dense",
    LapseEngine.Format.COMPACT to "Compact",
    LapseEngine.Format.CYCLE to "Cycle",
    LapseEngine.Format.DAYS to "Jours",
)

private val SECONDS_LABELS = listOf(
    LapseEngine.SecondsMode.RING to "Anneau",
    LapseEngine.SecondsMode.HOURGLASS to "Sablier",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigScreen() {
    val context = LocalContext.current
    val prefs = remember { Config.prefs(context) }
    val zone = remember { ZoneId.systemDefault() }
    val engine = remember { LapseEngine(zone) }
    val renderer = remember { LapseRenderer() }

    // Un lapse par onglet ; l'onglet sélectionné est aussi le lapse actif (Glyph).
    var lapses by remember {
        mutableStateOf((0 until Config.LAPSE_COUNT).map { Config.readLapse(prefs, it, zone) })
    }
    var selectedTab by remember { mutableStateOf(Config.activeIndex(prefs)) }
    val current = lapses[selectedTab]
    val refMillis = current.ref
    val format = current.format
    val secondsMode = current.seconds

    var frame by remember { mutableStateOf(IntArray(25 * 25)) }
    var diff by remember { mutableStateOf<TimeBreakdown.Diff?>(null) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }

    // Dates favorites : liste persistée, commune à tous les lapse.
    var savedDates by remember { mutableStateOf(Config.savedDates(prefs)) }
    var savedExpanded by remember { mutableStateOf(true) }
    var showAddDate by remember { mutableStateOf(false) }
    var showAddTime by remember { mutableStateOf(false) }
    var addDraftMillis by remember { mutableStateOf(0L) }

    fun now() = System.nanoTime() / 1e9

    fun updateCurrent(cfg: Config.LapseConfig) {
        lapses = lapses.toMutableList().also { it[selectedTab] = cfg }
        Config.writeLapse(prefs, selectedTab, cfg)
    }

    fun selectTab(i: Int) {
        selectedTab = i
        Config.setActiveIndex(prefs, i) // le toy bascule (avec slide) sur ce lapse
    }

    fun persistSaved(list: List<Long>) {
        savedDates = list
        Config.setSavedDates(prefs, list)
    }

    LaunchedEffect(Unit) {
        while (true) {
            val cur = lapses[selectedTab]
            if (engine.refMillis != cur.ref) engine.setRef(cur.ref)
            if (engine.format != cur.format) engine.setFormatQuiet(cur.format)
            engine.secondsMode = cur.seconds
            engine.drainEvents()
            val snap = engine.update(System.currentTimeMillis(), now())
            frame = renderer.render(snap)
            diff = snap.diff
            delay(33)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BG)
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LapseRail(selectedTab, lapses) { selectTab(it) }
        Spacer(Modifier.height(14.dp))

        MatrixPreview(frame)
        Spacer(Modifier.height(10.dp))
        DiffReadout(refMillis, diff, zone)
        Spacer(Modifier.height(24.dp))

        // --- Activation (lapse 2 et 3 seulement ; le lapse 1 est toujours actif) ---
        if (selectedTab != 0) {
            SettingCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Activer ce lapse",
                            color = LABEL,
                            fontSize = 17.sp,
                            fontFamily = FontFamily.Serif,
                        )
                        Text(
                            "Inclus dans la rotation (appui long)",
                            color = MUTED,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    SquareSwitch(current.enabled) {
                        updateCurrent(current.copy(enabled = !current.enabled))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // --- Carte Date et heure ---
        val ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(refMillis), zone)
        SettingCard {
            Legend("Date et heure")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton(
                    text = ldt.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.FRENCH)),
                    bg = ACCENT,
                    modifier = Modifier.weight(1.5f),
                ) { showDate = true }
                PillButton(
                    text = ldt.format(DateTimeFormatter.ofPattern("HH:mm")),
                    bg = GREY_BTN,
                    modifier = Modifier.weight(1f),
                ) { showTime = true }
            }
        }

        Spacer(Modifier.height(16.dp))

        // --- Carte Dates sauvegardées (collapsible, accent jaune) ---
        SettingCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { savedExpanded = !savedExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Dates sauvegardées",
                    color = LABEL,
                    fontSize = 17.sp,
                    fontFamily = FontFamily.Serif,
                )
                Canvas(
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(if (savedExpanded) 180f else 0f),
                ) {
                    val w = size.width
                    val h = size.height
                    val sw = 2.dp.toPx()
                    drawLine(MUTED, Offset(w * 0.22f, h * 0.38f), Offset(w * 0.5f, h * 0.64f), sw, StrokeCap.Round)
                    drawLine(MUTED, Offset(w * 0.5f, h * 0.64f), Offset(w * 0.78f, h * 0.38f), sw, StrokeCap.Round)
                }
            }
            if (savedExpanded) {
                Spacer(Modifier.height(14.dp))
                savedDates.forEach { millis ->
                    SavedDateRow(
                        millis = millis,
                        zone = zone,
                        applied = millis == refMillis,
                        onApply = { updateCurrent(current.copy(ref = millis)) },
                        onDelete = { persistSaved(savedDates - millis) },
                    )
                    Spacer(Modifier.height(10.dp))
                }
                if (savedDates.size < Config.SAVED_MAX) {
                    AddDateButton { addDraftMillis = System.currentTimeMillis(); showAddDate = true }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // --- Carte Style (sans titre) ---
        SettingCard {
            Legend("Animation")
            SelectField(
                options = SECONDS_LABELS,
                selected = secondsMode,
            ) { updateCurrent(current.copy(seconds = it)) }

            Spacer(Modifier.height(18.dp))

            Legend("Style d'affichage")
            SelectField(
                options = LapseEngine.Format.entries.map { it to (FORMAT_LABELS[it] ?: it.name) },
                selected = format,
            ) { updateCurrent(current.copy(format = it)) }
        }

        // Respiration en bas : la dernière carte peut scroller sans rester
        // collée au bord de l'écran.
        Spacer(Modifier.height(48.dp))
    }

    if (showDate) {
        val ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(refMillis), zone)
        val state = rememberDatePickerState(
            initialSelectedDateMillis = ldt.toLocalDate()
                .atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { utc ->
                        val date = Instant.ofEpochMilli(utc)
                            .atZone(ZoneId.of("UTC")).toLocalDate()
                        updateCurrent(
                            current.copy(
                                ref = date.atTime(ldt.toLocalTime()).atZone(zone)
                                    .toInstant().toEpochMilli()
                            )
                        )
                    }
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDate = false }) { Text("Annuler") }
            },
        ) { DatePicker(state) }
    }

    if (showTime) {
        val ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(refMillis), zone)
        val state = rememberTimePickerState(
            initialHour = ldt.hour,
            initialMinute = ldt.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = {
                    updateCurrent(
                        current.copy(
                            ref = ldt.toLocalDate().atTime(state.hour, state.minute)
                                .atZone(zone).toInstant().toEpochMilli()
                        )
                    )
                    showTime = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTime = false }) { Text("Annuler") }
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TimePicker(state)
                }
            },
        )
    }

    // Ajout d'une date favorite : date puis heure, puis append (max SAVED_MAX).
    if (showAddDate) {
        val base = LocalDateTime.ofInstant(Instant.ofEpochMilli(addDraftMillis), zone)
        val state = rememberDatePickerState(
            initialSelectedDateMillis = base.toLocalDate()
                .atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showAddDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { utc ->
                        val date = Instant.ofEpochMilli(utc)
                            .atZone(ZoneId.of("UTC")).toLocalDate()
                        addDraftMillis = date.atTime(base.toLocalTime()).atZone(zone)
                            .toInstant().toEpochMilli()
                    }
                    showAddDate = false
                    showAddTime = true
                }) { Text("Suivant") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDate = false }) { Text("Annuler") }
            },
        ) { DatePicker(state) }
    }

    if (showAddTime) {
        val draft = LocalDateTime.ofInstant(Instant.ofEpochMilli(addDraftMillis), zone)
        val state = rememberTimePickerState(
            initialHour = draft.hour,
            initialMinute = draft.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showAddTime = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = draft.toLocalDate()
                        .atTime(state.hour, state.minute)
                        .atZone(zone).toInstant().toEpochMilli()
                    if (savedDates.size < Config.SAVED_MAX) persistSaved(savedDates + millis)
                    showAddTime = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showAddTime = false }) { Text("Annuler") }
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TimePicker(state)
                }
            },
        )
    }
}

/**
 * Sélecteur de lapse : 3 sabliers filaires (deux triangles vides) posés sur une
 * ligne de points, neck aligné sur la ligne. L'actif est rouge, les autres blancs.
 * Chiffres romains I/II/III au-dessus. Un point retiré de chaque côté des sabliers.
 */
@Composable
private fun LapseRail(
    selected: Int,
    lapses: List<Config.LapseConfig>,
    onSelect: (Int) -> Unit,
) {
    val rail = 60.dp
    val slot = 18.dp
    // grille : points ('d'), sabliers ('h'), respiration ('') autour des sabliers
    val pattern = listOf(
        "d", "d", "", "h", "", "d", "d", "d", "", "h", "", "d", "d", "d", "", "h", "", "d", "d",
    )
    val roman = listOf("I", "II", "III")
    var hg = 0
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        pattern.forEach { type ->
            when (type) {
                "d" -> Box(
                    Modifier.width(slot).height(rail),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(3.dp).clip(CircleShape).background(MUTED))
                }

                "h" -> {
                    val i = hg++
                    val color = if (i == selected) ACCENT else TXT
                    Box(
                        modifier = Modifier
                            .width(slot)
                            .height(rail)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onSelect(i) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Canvas(Modifier.size(width = 15.dp, height = 28.dp)) {
                            val w = size.width
                            val h = size.height
                            val mid = h / 2f
                            val p = Path().apply {
                                moveTo(0f, 0f); lineTo(w, 0f); lineTo(w / 2f, mid); close()
                                moveTo(0f, h); lineTo(w, h); lineTo(w / 2f, mid); close()
                            }
                            drawPath(
                                p, color,
                                style = Stroke(width = 1.7.dp.toPx(), join = StrokeJoin.Round),
                            )
                        }
                        Text(
                            roman[i],
                            color = color,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Serif,
                            modifier = Modifier.align(Alignment.TopCenter),
                        )
                    }
                }

                else -> Spacer(Modifier.width(slot))
            }
        }
    }
}

/** Interrupteur carré/rectangulaire (angles droits), rouge = activé. */
@Composable
private fun SquareSwitch(checked: Boolean, onToggle: () -> Unit) {
    val pad = 3.dp
    Box(
        modifier = Modifier
            .size(width = 52.dp, height = 28.dp)
            .background(if (checked) ACCENT else GREY_BTN)
            .border(1.dp, if (checked) ACCENT else SEL_LINE)
            .clickable(onClick = onToggle),
    ) {
        Box(
            modifier = Modifier
                .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(pad)
                .size(22.dp)
                .background(if (checked) Color.White else MUTED),
        )
    }
}

@Composable
private fun SettingCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(CARD)
            .border(1.dp, CARD_LINE, RoundedCornerShape(22.dp))
            .padding(horizontal = 20.dp, vertical = 22.dp),
        content = content,
    )
}

@Composable
private fun Legend(text: String) {
    Text(
        text,
        color = LABEL,
        fontSize = 17.sp,
        fontFamily = FontFamily.Serif,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}

@Composable
private fun RowScope.PillButton(
    text: String,
    bg: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
        )
    }
}

@Composable
private fun <T> SelectField(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = options.firstOrNull { it.first == selected }?.second ?: ""
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.5.dp, if (expanded) ACCENT else SEL_LINE, RoundedCornerShape(12.dp))
                .clickable { expanded = true }
                .padding(horizontal = 18.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(currentLabel, color = TXT, fontSize = 15.sp, fontFamily = FontFamily.Monospace)
            Text("▼", color = ACCENT, fontSize = 10.sp)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MENU_BG),
        ) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            label,
                            color = if (value == selected) ACCENT else TXT,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 15.sp,
                        )
                    },
                    onClick = { onSelect(value); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun SavedDateRow(
    millis: Long,
    zone: ZoneId,
    applied: Boolean,
    onApply: () -> Unit,
    onDelete: () -> Unit,
) {
    val ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)
    val dateStr = ldt.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.FRENCH))
    val timeStr = ldt.format(DateTimeFormatter.ofPattern("HH:mm"))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(GREY_BTN)
            .then(
                if (applied) Modifier.border(1.5.dp, YELLOW, RoundedCornerShape(14.dp))
                else Modifier
            )
            .clickable(onClick = onApply),
    ) {
        // Contenu (carte = appliquer) ; padding droit pour laisser la zone de la croix.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 48.dp, top = 15.dp, bottom = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                dateStr,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                timeStr,
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        // Croix de suppression, par-dessus : grande zone de clic pleine hauteur.
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(48.dp)
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(10.5.dp)) {
                val s = size.minDimension
                val sw = 1.7.dp.toPx()
                drawLine(MUTED, Offset(0f, 0f), Offset(s, s), sw, StrokeCap.Round)
                drawLine(MUTED, Offset(s, 0f), Offset(0f, s), sw, StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun AddDateButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .drawBehind {
                drawRoundRect(
                    color = SEL_LINE,
                    cornerRadius = CornerRadius(14.dp.toPx()),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(11f, 8f)),
                    ),
                )
            }
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "+",
            color = YELLOW,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "Nouvelle date",
            color = TXT,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun DiffReadout(refMillis: Long, diff: TimeBreakdown.Diff?, zone: ZoneId) {
    if (diff == null) return
    val ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(refMillis), zone)
    val dateStr = ldt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRENCH))
    val head = if (diff.direction == TimeBreakdown.Direction.SINCE) "Depuis le" else "Jusqu'au"
    val parts = buildList {
        if (diff.years > 0) add("${diff.years} an" + if (diff.years > 1) "s" else "")
        if (diff.months > 0) add("${diff.months} mois")
        if (diff.days > 0) add("${diff.days} jour" + if (diff.days > 1) "s" else "")
        if (diff.hours > 0) add("${diff.hours} h")
        if (diff.minutes > 0) add("${diff.minutes} min")
        add("${diff.seconds} s")
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "$head $dateStr",
            color = ACCENT,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            parts.joinToString(" "),
            color = MUTED,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun MatrixPreview(frame: IntArray) {
    Canvas(
        modifier = Modifier
            .size(300.dp)
            .background(Color(0xFF0B0B0D), CircleShape)
    ) {
        val n = 25
        val cell = size.width / n
        val led = cell * 0.66f
        val inset = (cell - led) / 2
        for (y in 0 until n) {
            for (x in 0 until n) {
                val dx = x - 12
                val dy = y - 12
                if (dx * dx + dy * dy > 12.5f * 12.5f) continue
                val topLeft = Offset(x * cell + inset, y * cell + inset)
                drawRect(
                    color = Color.White.copy(alpha = 0.05f),
                    topLeft = topLeft,
                    size = Size(led, led),
                )
                val v = frame[y * n + x]
                if (v > 5) {
                    drawRect(
                        color = Color(0xFFF8F8F4).copy(alpha = v / 255f),
                        topLeft = topLeft,
                        size = Size(led, led),
                    )
                }
            }
        }
    }
}
