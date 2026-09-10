package red.suns.haloglyph.lapse.settings

import android.content.Intent
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
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import red.suns.haloglyph.core.matrix.Frame
import red.suns.haloglyph.core.matrix.MatrixSpec
import red.suns.haloglyph.core.ui.Breadcrumb
import red.suns.haloglyph.core.ui.DashedAddRow
import red.suns.haloglyph.core.ui.HaloCard
import red.suns.haloglyph.core.ui.HaloCardBg
import red.suns.haloglyph.core.ui.HaloCardRadius
import red.suns.haloglyph.core.ui.HaloControlBg
import red.suns.haloglyph.core.ui.HaloFaint
import red.suns.haloglyph.core.ui.HaloField
import red.suns.haloglyph.core.ui.HaloGutter
import red.suns.haloglyph.core.ui.HaloHair
import red.suns.haloglyph.core.ui.HaloMenu
import red.suns.haloglyph.core.ui.HaloMenuItem
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
import red.suns.haloglyph.core.ui.PillButton
import red.suns.haloglyph.core.ui.ScreenTitle
import red.suns.haloglyph.core.ui.SelectChevron
import red.suns.haloglyph.lapse.LapseConfig
import red.suns.haloglyph.lapse.R
import red.suns.haloglyph.lapse.engine.LapseEngine
import red.suns.haloglyph.lapse.engine.TimeBreakdown
import red.suns.haloglyph.lapse.render.LapseRenderer
import red.suns.haloglyph.lapse.render.MatrixLabels
import java.time.Instant
import java.time.LocalDate
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
 * Porté de l'app de réglages de GlyphLapse, dont il garde la mécanique — des
 * lapse indépendants, aperçu live partageant moteur et renderer avec la matrice,
 * dates favorites communes — et dont il abandonne le style : cartes sans
 * bordure, contrôles de la maquette Haloglyph, aucune glose sous un réglage.
 *
 * Ce qui a quitté l'écran, et pourquoi :
 *
 * - **la langue** remonte dans les réglages de l'application (PRODUIT §5.4) :
 *   une valeur pour toute l'app, la matrice comprise ;
 * - **le rail de sabliers**, remplacé par le titre — le nom du lapse actif,
 *   chevron vers le bas — qui ouvre un quick switch (sélection seule) ;
 * - **la suppression nue du bas**, centralisée dans [LapseManageActivity], seul
 *   endroit qui réordonne, renomme et supprime ;
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

    var lapses by remember { mutableStateOf(LapseConfig.readAll(prefs, zone)) }
    var selected by remember { mutableIntStateOf(LapseConfig.activeIndex(prefs)) }
    val current = lapses[selected]

    // La liste de gestion vit dans sa propre Activity, mais partage les mêmes
    // préférences : au retour, on relit tout plutôt que d'attendre un résultat
    // — un ajout, une suppression ou un réordonnancement s'y sont peut-être
    // produits, et cet écran n'a aucune raison de les rejouer lui-même.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                lapses = LapseConfig.readAll(prefs, zone)
                selected = LapseConfig.activeIndex(prefs)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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

    /** Choisir un lapse le rend **actif sur la matrice**. Rien d'autre : toute
     *  la mécanique de renommage, réordonnancement et suppression vit dans
     *  [LapseManageActivity], que le quick switch atteint en dernière ligne. */
    fun select(index: Int) {
        selected = index
        LapseConfig.setActiveIndex(prefs, index)
    }

    fun persistSaved(list: List<Long>) {
        savedDates = list
        LapseConfig.setSavedDates(prefs, list)
    }

    /**
     * L'aperçu tourne pour de vrai : même moteur, même renderer, mêmes
     * préférences que le service Glyph. Cadencé par `withFrameNanos`, donc
     * arrêté de lui-même dès que l'écran s'éteint.
     *
     * **Et arrêté aussi tant qu'un sélecteur est ouvert.** La boucle republie
     * `brightness` à chaque vsync, ce qui recompose tout le corps de l'écran —
     * dialogue compris. Un sélecteur d'heure encaissait ; le sélecteur de date,
     * qui recompose une grille de 42 jours, sautait des images à chaque
     * changement de mois. L'aperçu est de toute façon caché derrière le
     * dialogue : le faire tourner ne servait qu'à ralentir ce qu'on regarde.
     */
    val pickerOpen = showDate || showTime || showAddDate || showAddTime
    LaunchedEffect(pickerOpen) {
        if (pickerOpen) return@LaunchedEffect
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
        Breadcrumb(
            listOf(stringResource(R.string.lapse_settings_parent), stringResource(R.string.toy_lapse_name)),
            onBack = onBack,
        )
        LapseTitle(
            lapses = lapses,
            selected = selected,
            onSelect = { select(it) },
            onManage = { context.startActivity(Intent(context, LapseManageActivity::class.java)) },
        )

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

        // Pas de label de section entre la lecture et les réglages : le titre de
        // l'écran nomme déjà le lapse, et « ACTIF » redisait ce que le point
        // rouge du quick switch dit mieux. Il ne reste que la respiration.
        Spacer(Modifier.height(18.dp))

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

        Spacer(Modifier.height(48.dp))
    }

    // ---------- sélecteurs système ----------

    if (showDate) {
        val base = LocalDateTime.ofInstant(Instant.ofEpochMilli(current.ref), zone)
        HaloDatePicker(
            initial = base,
            zone = zone,
            confirmLabel = stringResource(R.string.action_ok),
            onDismiss = { showDate = false },
            onConfirm = { date ->
                updateCurrent(
                    current.copy(
                        ref = date.atTime(base.toLocalTime()).atZone(zone).toInstant().toEpochMilli(),
                    ),
                )
                showDate = false
            },
        )
    }

    if (showTime) {
        val base = LocalDateTime.ofInstant(Instant.ofEpochMilli(current.ref), zone)
        HaloTimePicker(
            initial = base,
            onDismiss = { showTime = false },
            onConfirm = { hour, minute ->
                updateCurrent(
                    current.copy(
                        ref = base.toLocalDate().atTime(hour, minute)
                            .atZone(zone).toInstant().toEpochMilli(),
                    ),
                )
                showTime = false
            },
        )
    }

    // Ajout d'une date favorite : date, puis heure, puis append (max SAVED_MAX).
    if (showAddDate) {
        val base = LocalDateTime.ofInstant(Instant.ofEpochMilli(addDraftMillis), zone)
        HaloDatePicker(
            initial = base,
            zone = zone,
            confirmLabel = stringResource(R.string.action_next),
            onDismiss = { showAddDate = false },
            onConfirm = { date ->
                addDraftMillis = date.atTime(base.toLocalTime()).atZone(zone)
                    .toInstant().toEpochMilli()
                showAddDate = false
                showAddTime = true
            },
        )
    }

    if (showAddTime) {
        val draft = LocalDateTime.ofInstant(Instant.ofEpochMilli(addDraftMillis), zone)
        HaloTimePicker(
            initial = draft,
            onDismiss = { showAddTime = false },
            onConfirm = { hour, minute ->
                val millis = draft.toLocalDate().atTime(hour, minute)
                    .atZone(zone).toInstant().toEpochMilli()
                if (savedDates.size < LapseConfig.SAVED_MAX) persistSaved(savedDates + millis)
                showAddTime = false
            },
        )
    }
}

// ---------------------------------------------------------------- sélecteurs

/**
 * Le calendrier de Material, habillé pour l'app.
 *
 * Ce qui change par rapport au dialogue nu :
 *
 * - **les actions sont des pilules**, la même forme que partout ailleurs. Les
 *   `TextButton` de Material étaient deux mots bleuâtres dans un coin ;
 * - **ni titre ni en-tête** : « Sélectionner une date » et la date en gros au-
 *   dessus du calendrier répétaient ce que le champ qu'on vient de toucher dit
 *   déjà, et l'en-tête se recompose à chaque changement de mois ;
 * - **l'amplitude d'années est bornée** au lieu des deux siècles par défaut. Un
 *   lapse pointe une date de vie, pas une date d'archive.
 *
 * L'état est confiné ici : le dialogue est **le seul** à se recomposer quand on
 * change de mois, plutôt que d'entraîner l'écran entier avec lui.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HaloDatePicker(
    initial: LocalDateTime,
    zone: ZoneId,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val thisYear = remember(zone) { LocalDate.now(zone).year }
    val state = rememberDatePickerState(
        // Le sélecteur raisonne en UTC : une date locale convertie dans le
        // fuseau du téléphone tomberait la veille à l'ouest de Greenwich.
        initialSelectedDateMillis = initial.toLocalDate()
            .atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli(),
        yearRange = IntRange(thisYear - YEARS_BACK, thisYear + YEARS_AHEAD),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            PillButton(text = confirmLabel, primary = true, small = true) {
                state.selectedDateMillis?.let { utc ->
                    onConfirm(Instant.ofEpochMilli(utc).atZone(ZoneId.of("UTC")).toLocalDate())
                } ?: onDismiss()
            }
        },
        dismissButton = {
            PillButton(text = stringResource(R.string.action_cancel), small = true, onClick = onDismiss)
        },
        shape = RoundedCornerShape(HaloCardRadius),
        colors = DatePickerDefaults.colors(containerColor = HaloCardBg),
    ) {
        DatePicker(
            state = state,
            title = null,
            headline = null,
            showModeToggle = false,
            colors = DatePickerDefaults.colors(containerColor = HaloCardBg),
        )
    }
}

/** Un anniversaire tient dedans, une échéance aussi. Au-delà, c'est de l'archive. */
private const val YEARS_BACK = 80
private const val YEARS_AHEAD = 30

/** L'horloge de Material, mêmes pilules que le calendrier. En 24 h, comme l'app. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HaloTimePicker(
    initial: LocalDateTime,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit,
) {
    val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            PillButton(text = stringResource(R.string.action_ok), primary = true, small = true) {
                onConfirm(state.hour, state.minute)
            }
        },
        dismissButton = {
            PillButton(text = stringResource(R.string.action_cancel), small = true, onClick = onDismiss)
        },
        shape = RoundedCornerShape(HaloCardRadius),
        containerColor = HaloCardBg,
        text = { Column(horizontalAlignment = Alignment.CenterHorizontally) { TimePicker(state) } },
    )
}

/**
 * Le titre, devenu sélecteur : le nom du lapse actif, chevron vers le bas.
 *
 * Remplace le rail de sabliers — plus de sens à un rail une fois le nombre de
 * lapse variable. Un tap ouvre un quick switch **de sélection seule** : choisir
 * y rend un lapse actif sur la matrice, rien de plus. Sa dernière ligne, seule
 * à porter l'accent, mène à [LapseManageActivity] pour tout le reste.
 */
@Composable
private fun LapseTitle(
    lapses: List<LapseConfig.Lapse>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onManage: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            ScreenTitle(lapses[selected].name, small = true)
            // Le même triangle que les sélecteurs des cartes, à l'échelle d'un
            // titre. Décalé vers le bas : il s'aligne sur la ligne de base du
            // texte, pas sur le milieu de sa boîte, qui inclut les jambages.
            SelectChevron(
                color = HaloFaint,
                width = 14.dp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        HaloMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            lapses.forEachIndexed { index, lapse ->
                HaloMenuItem(
                    label = lapse.name,
                    size = MENU_TITLE_SIZE,
                    leading = {
                        // La pastille garde sa place même vide : les noms
                        // s'alignent, actif ou non.
                        if (index == selected) {
                            Canvas(Modifier.size(6.dp)) { drawCircle(HaloRed, size.minDimension / 2f) }
                        } else {
                            Spacer(Modifier.size(6.dp))
                        }
                    },
                ) { onSelect(index); expanded = false }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 10.dp).background(HaloHair))
            HaloMenuItem(
                label = stringResource(R.string.lapse_manage_title),
                size = MENU_TITLE_SIZE,
                color = HaloRed,
                leading = { Spacer(Modifier.size(6.dp)) },
            ) { expanded = false; onManage() }
        }
    }
}

/** Les options du titre se lisent à la taille d'un titre, pas d'un réglage. */
private val MENU_TITLE_SIZE = 16.sp

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
