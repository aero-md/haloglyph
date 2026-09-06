package red.suns.haloglyph.lapse.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import red.suns.haloglyph.core.matrix.Frame
import red.suns.haloglyph.core.matrix.MatrixSpec
import red.suns.haloglyph.core.ui.BackBar
import red.suns.haloglyph.core.ui.BareAction
import red.suns.haloglyph.core.ui.DashedAddRow
import red.suns.haloglyph.core.ui.HaloCard
import red.suns.haloglyph.core.ui.HaloControlBg
import red.suns.haloglyph.core.ui.HaloFaint
import red.suns.haloglyph.core.ui.HaloField
import red.suns.haloglyph.core.ui.HaloGutter
import red.suns.haloglyph.core.ui.HaloMuted
import red.suns.haloglyph.core.ui.HaloRed
import red.suns.haloglyph.core.ui.HaloScreen
import red.suns.haloglyph.core.ui.HaloSelect
import red.suns.haloglyph.core.ui.HaloText
import red.suns.haloglyph.core.ui.HaloglyphTheme
import red.suns.haloglyph.core.ui.Legend
import red.suns.haloglyph.core.ui.LinkedTiles
import red.suns.haloglyph.core.ui.MatrixPreview
import red.suns.haloglyph.core.ui.MonoValue
import red.suns.haloglyph.core.ui.ScreenTitle
import red.suns.haloglyph.core.ui.SectionLabel
import red.suns.haloglyph.lapse.LapseConfig
import red.suns.haloglyph.lapse.R
import red.suns.haloglyph.lapse.engine.LapseEngine
import red.suns.haloglyph.lapse.engine.TimeBreakdown
import red.suns.haloglyph.lapse.render.LapseRenderer
import red.suns.haloglyph.lapse.render.MatrixLabels
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Les réglages de Lapse — écran 02 de la maquette.
 *
 * Une `Activity` du module du toy, et non un écran du hub : le toy porte ses
 * quatre surfaces (matrice, widget, vignette, réglages) dans son propre module,
 * et `app` ne fait que la lancer. Ajouter un toy n'ajoute pas une ligne au hub.
 *
 * Porté de l'app de réglages de GlyphLapse, dont il garde la mécanique — trois
 * lapse indépendants, aperçu live partageant moteur et renderer avec la matrice,
 * dates favorites communes — et dont il abandonne le style : cartes sans
 * bordure, contrôles de la maquette Haloglyph, aucune glose sous un réglage.
 *
 * Ce qui a quitté l'écran, et pourquoi :
 *
 * - **la langue** remonte dans les réglages de l'application (PRODUIT §5.4) :
 *   une valeur pour toute l'app, la matrice comprise ;
 * - **la bascule « activer ce lapse »**, remplacée par l'action nue du bas ;
 * - **le repli des dates sauvegardées** : cinq entrées au maximum, ça tient ;
 * - **tous les paragraphes d'explication**.
 */
class LapseSettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HaloglyphTheme {
                LapseSettingsScreen(onBack = { finish() })
            }
        }
    }
}

/** L'heure : `HH:mm` partout, le sélecteur d'heure étant lui aussi en 24 h. */
private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")

/** Le rail nomme les trois lapse en chiffres romains, comme la matrice. */
private val ROMAN = listOf("I", "II", "III")

/**
 * Une date dans la langue courante — « 23 juil. 2026 », « Jul 23, 2026 ».
 *
 * `ofLocalizedDate` et non un motif à nous : l'ordre des composantes change
 * d'une langue à l'autre, et un `dd MMM yyyy` retraduit donnerait
 * « 23 Jul 2026 » à un lecteur anglophone qui attend le mois devant.
 */
private fun dateLabel(ldt: LocalDateTime): String =
    ldt.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LapseSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { LapseConfig.prefs(context) }
    val zone = remember { ZoneId.systemDefault() }
    val spec = MatrixSpec.Phone3
    val engine = remember { LapseEngine(zone) }
    val renderer = remember { LapseRenderer(spec) }

    var lapses by remember {
        mutableStateOf((0 until LapseConfig.LAPSE_COUNT).map { LapseConfig.readLapse(prefs, it, zone) })
    }
    var selected by remember { mutableIntStateOf(LapseConfig.activeIndex(prefs)) }
    val current = lapses[selected]

    var brightness by remember { mutableStateOf(IntArray(spec.cellCount)) }
    var diff by remember { mutableStateOf<TimeBreakdown.Diff?>(null) }

    var savedDates by remember { mutableStateOf(LapseConfig.savedDates(prefs)) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var showAddDate by remember { mutableStateOf(false) }
    var showAddTime by remember { mutableStateOf(false) }
    var addDraftMillis by remember { mutableLongStateOf(0L) }

    fun updateCurrent(cfg: LapseConfig.Lapse) {
        lapses = lapses.toMutableList().also { it[selected] = cfg }
        LapseConfig.writeLapse(prefs, selected, cfg)
    }

    /**
     * Choisir un lapse le rend **actif sur la matrice**, comme le rail de
     * GlyphLapse, et le réactive s'il avait été supprimé.
     *
     * La réactivation n'est pas une commodité : le service ne fait tourner que
     * les lapse activés (`nextEnabledIndex`), donc rendre actif un lapse
     * désactivé mettrait la matrice et l'app en désaccord. Ouvrir un lapse
     * supprimé, c'est le recréer — la question « supprimer ou désactiver ? »
     * reste ouverte côté produit, mais le code, lui, reste cohérent.
     */
    fun select(index: Int) {
        selected = index
        if (!lapses[index].enabled) {
            val revived = lapses[index].copy(enabled = true)
            lapses = lapses.toMutableList().also { it[index] = revived }
            LapseConfig.writeLapse(prefs, index, revived)
        }
        LapseConfig.setActiveIndex(prefs, index)
    }

    /** Sort le lapse de la rotation et remet ses réglages à zéro. L'emplacement reste. */
    fun deleteCurrent() {
        val blank = LapseConfig.Lapse(
            ref = LapseConfig.defaultRef(selected, zone),
            format = LapseEngine.Format.DETAIL2,
            seconds = LapseEngine.SecondsMode.RING,
            enabled = false,
        )
        lapses = lapses.toMutableList().also { it[selected] = blank }
        LapseConfig.writeLapse(prefs, selected, blank)
        // Le lapse I est toujours actif : c'est vers lui qu'on retombe.
        selected = 0
        LapseConfig.setActiveIndex(prefs, 0)
    }

    fun persistSaved(list: List<Long>) {
        savedDates = list
        LapseConfig.setSavedDates(prefs, list)
    }

    // L'aperçu tourne pour de vrai : même moteur, même renderer, mêmes
    // préférences que le service Glyph. Cadencé par `withFrameNanos`, donc
    // arrêté de lui-même dès que l'écran s'éteint.
    LaunchedEffect(Unit) {
        val frame = Frame(spec)
        while (true) {
            withFrameNanos { }
            renderer.labels = MatrixLabels.current()
            val cfg = lapses[selected]
            if (engine.refMillis != cfg.ref) engine.setRef(cfg.ref)
            if (engine.format != cfg.format) engine.setFormatQuiet(cfg.format)
            engine.secondsMode = cfg.seconds
            engine.drainEvents()
            val snap = engine.update(System.currentTimeMillis(), System.nanoTime() / 1e9)
            frame.clear()
            renderer.render(frame, snap)
            brightness = frame.toBrightness().copyOf()
            diff = snap.diff
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HaloScreen)
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp),
    ) {
        BackBar(stringResource(R.string.lapse_settings_parent), onBack = onBack)
        ScreenTitle(
            stringResource(R.string.toy_lapse_name),
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
            small = true,
        )

        LapseRail(selected = selected, lapses = lapses, onSelect = { select(it) })

        // ---------- l'aperçu, en tête ----------

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            MatrixPreview(brightness = brightness, modifier = Modifier.size(252.dp), spec = spec)
            DiffReadout(current.ref, diff, zone)
        }

        SectionLabel(
            text = stringResource(R.string.lapse_section, ROMAN[selected]),
            trailing = stringResource(R.string.lapse_state_active),
        )

        // ---------- appariées : les deux décident de ce que la matrice montre ----------

        val ring = stringResource(R.string.seconds_ring)
        val hourglass = stringResource(R.string.seconds_hourglass)
        val dense = stringResource(R.string.format_dense)
        val compact = stringResource(R.string.format_compact)
        val cycle = stringResource(R.string.format_cycle)
        val days = stringResource(R.string.format_days)

        LinkedTiles(
            left = {
                Legend(stringResource(R.string.card_animation))
                Spacer(Modifier.weight(1f))
                HaloSelect(
                    options = LapseEngine.SecondsMode.entries.map {
                        it to when (it) {
                            LapseEngine.SecondsMode.RING -> ring
                            LapseEngine.SecondsMode.HOURGLASS -> hourglass
                        }
                    },
                    selected = current.seconds,
                ) { updateCurrent(current.copy(seconds = it)) }
            },
            right = {
                Legend(stringResource(R.string.card_display_style))
                Spacer(Modifier.weight(1f))
                HaloSelect(
                    // `when` exhaustif plutôt qu'une table à repli sur le nom de
                    // l'enum : un format ajouté sans étiquette ne compile plus,
                    // au lieu de s'afficher « DETAIL3 » chez l'utilisateur.
                    options = LapseEngine.Format.entries.map {
                        it to when (it) {
                            LapseEngine.Format.DETAIL2 -> dense
                            LapseEngine.Format.COMPACT -> compact
                            LapseEngine.Format.CYCLE -> cycle
                            LapseEngine.Format.DAYS -> days
                        }
                    },
                    selected = current.format,
                ) { updateCurrent(current.copy(format = it)) }
            },
        )

        Spacer(Modifier.height(HaloGutter))

        // ---------- date de référence ----------

        val ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(current.ref), zone)
        HaloCard {
            Legend(stringResource(R.string.card_datetime))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HaloField(
                    text = dateLabel(ldt),
                    modifier = Modifier.weight(1f),
                    accent = true,
                ) { showDate = true }
                HaloField(text = ldt.format(TIME_FMT), modifier = Modifier.width(96.dp)) {
                    showTime = true
                }
            }
        }

        Spacer(Modifier.height(HaloGutter))

        // ---------- dates favorites ----------

        HaloCard {
            Legend(stringResource(R.string.card_saved_dates))
            savedDates.forEach { millis ->
                SavedDateRow(
                    millis = millis,
                    zone = zone,
                    applied = millis == current.ref,
                    onApply = { updateCurrent(current.copy(ref = millis)) },
                    onDelete = { persistSaved(savedDates - millis) },
                )
            }
            if (savedDates.size < LapseConfig.SAVED_MAX) {
                DashedAddRow(stringResource(R.string.saved_date_add)) {
                    addDraftMillis = System.currentTimeMillis()
                    showAddDate = true
                }
            }
        }

        // Le lapse I est toujours actif : il n'a pas de bouton de suppression.
        if (selected != 0) {
            BareAction(
                text = stringResource(R.string.lapse_delete),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 18.dp),
                onClick = { deleteCurrent() },
            )
        }

        Spacer(Modifier.height(48.dp))
    }

    // ---------- sélecteurs système ----------

    if (showDate) {
        val base = LocalDateTime.ofInstant(Instant.ofEpochMilli(current.ref), zone)
        val state = rememberDatePickerState(
            initialSelectedDateMillis = base.toLocalDate()
                .atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { utc ->
                        val date = Instant.ofEpochMilli(utc).atZone(ZoneId.of("UTC")).toLocalDate()
                        updateCurrent(
                            current.copy(
                                ref = date.atTime(base.toLocalTime()).atZone(zone)
                                    .toInstant().toEpochMilli()
                            )
                        )
                    }
                    showDate = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDate = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        ) { DatePicker(state) }
    }

    if (showTime) {
        val base = LocalDateTime.ofInstant(Instant.ofEpochMilli(current.ref), zone)
        val state = rememberTimePickerState(base.hour, base.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = {
                    updateCurrent(
                        current.copy(
                            ref = base.toLocalDate().atTime(state.hour, state.minute)
                                .atZone(zone).toInstant().toEpochMilli()
                        )
                    )
                    showTime = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showTime = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) { TimePicker(state) }
            },
        )
    }

    // Ajout d'une date favorite : date, puis heure, puis append (max SAVED_MAX).
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
                        val date = Instant.ofEpochMilli(utc).atZone(ZoneId.of("UTC")).toLocalDate()
                        addDraftMillis = date.atTime(base.toLocalTime()).atZone(zone)
                            .toInstant().toEpochMilli()
                    }
                    showAddDate = false
                    showAddTime = true
                }) { Text(stringResource(R.string.action_next)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddDate = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        ) { DatePicker(state) }
    }

    if (showAddTime) {
        val draft = LocalDateTime.ofInstant(Instant.ofEpochMilli(addDraftMillis), zone)
        val state = rememberTimePickerState(draft.hour, draft.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showAddTime = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = draft.toLocalDate().atTime(state.hour, state.minute)
                        .atZone(zone).toInstant().toEpochMilli()
                    if (savedDates.size < LapseConfig.SAVED_MAX) persistSaved(savedDates + millis)
                    showAddTime = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddTime = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) { TimePicker(state) }
            },
        )
    }
}

/**
 * Le rail des trois lapse : des sabliers filaires posés sur une ligne de points.
 *
 * Le sablier n'est pas une décoration : c'est le même objet que le mode
 * « sablier » de la matrice, et il dit qu'on parle de temps qui passe. L'actif
 * est en accent, un lapse supprimé est atténué — la lecture de l'état est dans
 * la couleur, pas dans un texte.
 */
@Composable
private fun LapseRail(
    selected: Int,
    lapses: List<LapseConfig.Lapse>,
    onSelect: (Int) -> Unit,
) {
    // Grille : points ('d'), sabliers ('h'), respiration ('') autour des sabliers.
    val pattern = listOf(
        "d", "d", "", "h", "", "d", "d", "d", "", "h", "", "d", "d", "d", "", "h", "", "d", "d",
    )
    var next = 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        pattern.forEach { kind ->
            when (kind) {
                "d" -> Box(
                    Modifier.width(16.dp).height(56.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.size(3.dp)) { drawCircle(HaloMuted, size.minDimension / 2f) }
                }

                "h" -> {
                    val index = next++
                    val color = when {
                        index == selected -> HaloRed
                        lapses[index].enabled -> HaloText
                        else -> HaloFaint
                    }
                    Box(
                        modifier = Modifier
                            .width(26.dp)
                            .height(56.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onSelect(index) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Canvas(Modifier.size(width = 15.dp, height = 26.dp)) {
                            val w = size.width
                            val h = size.height
                            val mid = h / 2f
                            val path = Path().apply {
                                moveTo(0f, 0f); lineTo(w, 0f); lineTo(w / 2f, mid); close()
                                moveTo(0f, h); lineTo(w, h); lineTo(w / 2f, mid); close()
                            }
                            drawPath(
                                path,
                                color,
                                style = Stroke(width = 1.7.dp.toPx(), join = StrokeJoin.Round),
                            )
                        }
                        Text(
                            ROMAN[index],
                            color = color,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Serif,
                            modifier = Modifier.align(Alignment.TopCenter),
                        )
                    }
                }

                else -> Spacer(Modifier.width(10.dp))
            }
        }
    }
}

/**
 * Une date favorite : la ligne entière applique, la croix supprime.
 *
 * La date appliquée se marque d'un filet et d'un point blancs. Pas du jaune
 * Nothing de l'app d'origine : l'app ne dépense qu'une couleur, et le rouge est
 * déjà pris par « ce que fait la matrice maintenant ».
 */
@Composable
private fun SavedDateRow(
    millis: Long,
    zone: ZoneId,
    applied: Boolean,
    onApply: () -> Unit,
    onDelete: () -> Unit,
) {
    val ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HaloControlBg)
            .then(
                if (applied) Modifier.border(1.5.dp, HaloText, RoundedCornerShape(14.dp))
                else Modifier
            )
            .clickable(onClick = onApply),
    ) {
        // Padding droit : la zone de la croix, qui a son propre clic.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 48.dp, top = 13.dp, bottom = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (applied) {
                Canvas(Modifier.size(7.dp)) { drawCircle(HaloText, size.minDimension / 2f) }
            }
            MonoValue(dateLabel(ldt), size = 13.5.sp)
            MonoValue(ldt.format(TIME_FMT), color = HaloMuted, size = 12.sp)
        }
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
                drawLine(HaloMuted, Offset(0f, 0f), Offset(s, s), sw, StrokeCap.Round)
                drawLine(HaloMuted, Offset(s, 0f), Offset(0f, s), sw, StrokeCap.Round)
            }
        }
    }
}

/**
 * La lecture sous l'aperçu : la date de référence, puis le delta en toutes
 * lettres. C'est le seul endroit de l'écran où la matrice est traduite en texte
 * — et c'est ce qui permet de vérifier que ce qu'elle abrège est juste.
 */
@Composable
private fun DiffReadout(refMillis: Long, diff: TimeBreakdown.Diff?, zone: ZoneId) {
    if (diff == null) return
    val ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(refMillis), zone)
    val head = stringResource(
        if (diff.direction == TimeBreakdown.Direction.SINCE) R.string.diff_since
        else R.string.diff_until
    )

    /* Les six unités sont lues sans condition, avant d'être triées :
       `pluralStringResource` est un composable, et un appel sous `if` dans une
       lambda de construction est le genre de chose qui compile ou non selon
       l'humeur du compilateur Compose. Ce sont six lectures de ressource, pas
       six requêtes réseau. */
    val years = pluralStringResource(R.plurals.diff_years, diff.years, diff.years)
    val months = pluralStringResource(R.plurals.diff_months, diff.months, diff.months)
    val days = pluralStringResource(R.plurals.diff_days, diff.days, diff.days)
    val h = stringResource(R.string.unit_hours_abbr)
    val min = stringResource(R.string.unit_minutes_abbr)
    val s = stringResource(R.string.unit_seconds_abbr)

    val parts = buildList {
        if (diff.years > 0) add(years)
        if (diff.months > 0) add(months)
        if (diff.days > 0) add(days)
        if (diff.hours > 0) add("${diff.hours} $h")
        if (diff.minutes > 0) add("${diff.minutes} $min")
        add("${diff.seconds} $s")
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "$head ${dateLabel(ldt)} ${ldt.format(TIME_FMT)}".uppercase(),
            color = HaloRed,
            fontSize = 11.sp,
            letterSpacing = 0.9.sp,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            parts.joinToString(" "),
            color = HaloMuted,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}
