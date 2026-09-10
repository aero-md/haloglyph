package red.suns.haloglyph.lapse.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import red.suns.haloglyph.core.ui.Breadcrumb
import red.suns.haloglyph.core.ui.DashedAddRow
import red.suns.haloglyph.core.ui.HaloControlBg
import red.suns.haloglyph.core.ui.HaloFaint
import red.suns.haloglyph.core.ui.HaloMuted
import red.suns.haloglyph.core.ui.HaloRed
import red.suns.haloglyph.core.ui.HaloScreen
import red.suns.haloglyph.core.ui.HaloText
import red.suns.haloglyph.core.ui.HaloglyphTheme
import red.suns.haloglyph.core.ui.MonoLabel
import red.suns.haloglyph.core.ui.ScreenTitle
import red.suns.haloglyph.lapse.LapseConfig
import red.suns.haloglyph.lapse.R
import red.suns.haloglyph.lapse.engine.LapseEngine
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Gérer les lapses — écran 03 de la maquette, atteint depuis le quick switch
 * du titre de [LapseSettingsActivity].
 *
 * Une liste combinée plutôt que trois affordances séparées : ≡ réordonne (appui
 * long puis glisser), **le nom s'édite en le touchant**, la croix supprime avec
 * confirmation — elle devient un ✓, comme ailleurs dans l'app.
 *
 * Il n'y a **aucun bouton de validation du nom**. On touche le texte, on tape, on
 * touche ailleurs : c'est enregistré. Un crayon pour ouvrir puis une coche pour
 * fermer demandaient deux gestes de plus pour dire ce que la sortie du champ dit
 * déjà — et laissaient en plus la question de ce que devient une frappe quittée
 * sans cocher. Ici, rien ne se perd : sortir du champ, revenir en arrière ou
 * valider au clavier écrivent tous les trois.
 *
 * Entre [LapseConfig.LAPSE_MIN] et [LapseConfig.LAPSE_MAX] lapse : la borne
 * basse éteint la croix plutôt que de l'expliquer, la borne haute retire la
 * ligne d'ajout plutôt que de la griser — même loi que les cinq dates
 * favorites du card voisin : l'état se lit dans la forme, pas dans un texte.
 */
class LapseManageActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HaloglyphTheme {
                LapseManageScreen(onBack = { finish() })
            }
        }
    }
}

/** Une ligne le temps de l'écran : un id stable, immunisé contre le
 *  réordonnancement, sous le [LapseConfig.Lapse] qu'il porte. */
private data class Entry(val id: Int, val lapse: LapseConfig.Lapse)

private val ROW_HEIGHT = 64.dp

/** L'écart entre deux lignes. Il compte : c'est la moitié du pas de la liste. */
private val ROW_GAP = 9.dp

/** Durée du glissement d'une carte qui cède sa place. Court : c'est un
 *  accusé de réception, pas une transition. */
private const val REORDER_MS = 190

@Composable
private fun LapseManageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { LapseConfig.prefs(context) }
    val zone = remember { ZoneId.systemDefault() }
    val keyboard = LocalSoftwareKeyboardController.current

    var nextId by remember { mutableIntStateOf(0) }
    var entries by remember {
        mutableStateOf(
            LapseConfig.readAll(prefs, zone).map { Entry(nextId++, it) },
        )
    }
    var activeId by remember {
        mutableIntStateOf(entries[LapseConfig.activeIndex(prefs).coerceIn(entries.indices)].id)
    }
    var editingId by remember { mutableStateOf<Int?>(null) }
    var draft by remember { mutableStateOf("") }
    var confirmDeleteId by remember { mutableStateOf<Int?>(null) }
    var dragId by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }

    val density = LocalDensity.current
    // Le pas de la liste, ligne **et** gouttière : c'est la distance dont une
    // carte se déplace quand elle cède sa place. Mesurer au seul corps de la
    // ligne faisait basculer l'ordre 9 dp trop tôt, et l'erreur s'accumulait à
    // chaque échange.
    val stridePx = with(density) { (ROW_HEIGHT + ROW_GAP).toPx() }

    // Persiste la liste entière et fait suivre l'actif par son id : supprimer
    // un lapse plus haut dans la liste ne doit pas faire « sauter » l'actif sur
    // un autre, et supprimer l'actif lui-même retombe sur le premier restant
    // plutôt que sur celui qui prend sa place à l'écran.
    fun persist(next: List<Entry>) {
        entries = next
        LapseConfig.writeAll(prefs, next.map { it.lapse })
        val idx = next.indexOfFirst { it.id == activeId }
        if (idx >= 0) {
            LapseConfig.setActiveIndex(prefs, idx)
        } else {
            activeId = next[0].id
            LapseConfig.setActiveIndex(prefs, 0)
        }
    }

    /**
     * Ferme l'édition en cours **en gardant ce qui a été tapé**.
     *
     * Appelée de partout : un appui ailleurs sur l'écran, le retour, la touche
     * de validation du clavier, et tout geste qui commence autre chose sur la
     * liste. Un nom vidé n'est pas un nom : le lapse garde alors le sien.
     */
    fun commitEdit() {
        val id = editingId ?: return
        editingId = null
        keyboard?.hide()
        val name = draft.trim()
        if (name.isEmpty()) return
        val target = entries.firstOrNull { it.id == id } ?: return
        if (target.lapse.name == name) return
        persist(entries.map { if (it.id == id) it.copy(lapse = it.lapse.copy(name = name)) else it })
    }

    fun startEdit(entry: Entry) {
        commitEdit()
        confirmDeleteId = null
        draft = entry.lapse.name
        editingId = entry.id
    }

    // Le retour système écrit lui aussi. Il n'annule pas l'écran : tant qu'un nom
    // est en cours d'édition, le premier retour le range — le suivant sort.
    BackHandler(enabled = editingId != null) { commitEdit() }

    val ring = stringResource(R.string.seconds_ring)
    val hourglass = stringResource(R.string.seconds_hourglass)
    val dense = stringResource(R.string.format_dense)
    val compact = stringResource(R.string.format_compact)
    val cycle = stringResource(R.string.format_cycle)
    val days = stringResource(R.string.format_days)
    fun tagFor(lapse: LapseConfig.Lapse): String {
        val format = when (lapse.format) {
            LapseEngine.Format.DETAIL2 -> dense
            LapseEngine.Format.COMPACT -> compact
            LapseEngine.Format.CYCLE -> cycle
            LapseEngine.Format.DAYS -> days
        }
        val seconds = when (lapse.seconds) {
            LapseEngine.SecondsMode.RING -> ring
            LapseEngine.SecondsMode.HOURGLASS -> hourglass
        }
        return "$format · $seconds"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HaloScreen)
            // Le fond de l'écran est le bouton « j'ai fini d'écrire ». Posé avant
            // le défilement dans la chaîne, donc en dehors : un glissement est
            // consommé par le défilement et n'est jamais pris pour un appui.
            .pointerInput(Unit) { detectTapGestures { commitEdit() } }
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp),
    ) {
        Breadcrumb(
            listOf(stringResource(R.string.toy_lapse_name), stringResource(R.string.lapse_manage_title)),
            onBack = { commitEdit(); onBack() },
        )
        ScreenTitle(
            stringResource(R.string.lapse_manage_title),
            modifier = Modifier.padding(start = 6.dp, bottom = 14.dp),
            small = true,
        )

        entries.forEachIndexed { index, entry ->
            key(entry.id) {
                val isDragging = dragId == entry.id

                /*
                 * La carte qui **cède sa place** glisse au lieu de se téléporter.
                 *
                 * Le réordonnancement est immédiat dans la liste — c'est lui qui
                 * fait autorité, et le doigt doit voir l'ordre changer sous lui
                 * sans retard. L'animation raccroche seulement l'image : la carte
                 * est reposée d'un pas là d'où elle vient, puis ramenée à zéro.
                 * Personne ne voit un saut, et rien dans l'état ne dépend de
                 * l'animation.
                 */
                val settle = remember { Animatable(0f) }
                var lastIndex by remember { mutableIntStateOf(index) }
                LaunchedEffect(index) {
                    val from = lastIndex
                    lastIndex = index
                    // La carte tenue par le doigt suit le doigt : elle n'a rien à
                    // rattraper, son décalage est déjà compensé pas à pas.
                    if (from == index || dragId == entry.id) return@LaunchedEffect
                    settle.snapTo((from - index) * stridePx)
                    settle.animateTo(0f, tween(REORDER_MS, easing = EaseOutCubic))
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(if (isDragging) 1f else 0f)
                        .offset {
                            IntOffset(0, (if (isDragging) dragOffset else settle.value).roundToInt())
                        }
                        .padding(bottom = ROW_GAP),
                ) {
                    LapseRow(
                        entry = entry,
                        tag = tagFor(entry.lapse),
                        isActive = entry.id == activeId,
                        isEditing = editingId == entry.id,
                        draft = draft,
                        isConfirmingDelete = confirmDeleteId == entry.id,
                        canDelete = entries.size > LapseConfig.LAPSE_MIN,
                        onDraftChange = { draft = it },
                        onDragStart = {
                            commitEdit()
                            confirmDeleteId = null
                            dragId = entry.id
                            dragOffset = 0f
                        },
                        onDrag = { deltaY ->
                            dragOffset += deltaY
                            val current = entries.indexOfFirst { it.id == entry.id }
                            val shift = (dragOffset / stridePx).roundToInt()
                            val target = (current + shift).coerceIn(0, entries.lastIndex)
                            if (target != current) {
                                entries = entries.toMutableList()
                                    .also { it.add(target, it.removeAt(current)) }
                                dragOffset -= (target - current) * stridePx
                            }
                        },
                        onDragEnd = {
                            dragId = null
                            dragOffset = 0f
                            persist(entries)
                        },
                        onStartEdit = { startEdit(entry) },
                        onCommitEdit = { commitEdit() },
                        onArmDelete = { commitEdit(); confirmDeleteId = entry.id },
                        onConfirmDelete = {
                            confirmDeleteId = null
                            persist(entries.filterNot { it.id == entry.id })
                        },
                    )
                }
            }
        }

        if (entries.size < LapseConfig.LAPSE_MAX) {
            DashedAddRow(stringResource(R.string.lapse_add)) {
                commitEdit()
                val index = entries.size
                val fresh = Entry(
                    id = nextId++,
                    lapse = LapseConfig.Lapse(
                        name = LapseConfig.defaultName(index),
                        ref = LapseConfig.defaultRef(index, zone),
                        format = LapseEngine.Format.DETAIL2,
                        seconds = LapseEngine.SecondsMode.RING,
                    ),
                )
                persist(entries + fresh)
                draft = fresh.lapse.name
                editingId = fresh.id
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

/** Hauteur réservée au nom, la même qu'il soit lu ou tapé. Sans elle, le passage
 *  en édition décale le sous-titre d'un ou deux pixels — assez pour se voir. */
private val NAME_HEIGHT = 20.dp

/** Le nom du lapse, dans les deux modes. Un seul style, donc une seule métrique. */
private val NAME_STYLE = TextStyle(
    fontFamily = FontFamily.Serif,
    fontSize = 14.5.sp,
    lineHeight = 18.sp,
)

@Composable
private fun LapseRow(
    entry: Entry,
    tag: String,
    isActive: Boolean,
    isEditing: Boolean,
    draft: String,
    isConfirmingDelete: Boolean,
    canDelete: Boolean,
    onDraftChange: (String) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onStartEdit: () -> Unit,
    onCommitEdit: () -> Unit,
    onArmDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .clip(RoundedCornerShape(14.dp))
            .background(HaloControlBg)
            .padding(start = 6.dp, end = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        // ---------- déplacement : appui long puis glisser ----------
        Box(
            modifier = Modifier
                .size(width = 28.dp, height = 40.dp)
                .pointerInput(entry.id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { onDragStart() },
                        onDragEnd = { onDragEnd() },
                        onDragCancel = { onDragEnd() },
                        onDrag = { change, delta -> change.consume(); onDrag(delta.y) },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(width = 15.dp, height = 11.dp)) {
                val sw = 1.4.dp.toPx()
                listOf(0f, size.height / 2f, size.height).forEach { y ->
                    drawLine(HaloMuted, Offset(0f, y), Offset(size.width, y), sw, StrokeCap.Round)
                }
            }
        }

        // ---------- nom + réglages courts ----------
        Column(
            modifier = Modifier
                .weight(1f)
                // Toucher le nom l'ouvre. Pas de `clickable` en mode édition :
                // le champ a déjà le sien, et le curseur doit pouvoir se poser
                // entre deux lettres.
                .then(if (isEditing) Modifier else Modifier.clickable(onClick = onStartEdit)),
        ) {
            Row(
                modifier = Modifier.height(NAME_HEIGHT),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // La pastille de l'actif reste pendant l'édition : sans elle, le
                // nom glisserait de 12 dp vers la gauche au premier appui.
                if (isActive) {
                    Canvas(Modifier.size(6.dp)) { drawCircle(HaloRed, size.minDimension / 2f) }
                }
                if (isEditing) {
                    val focusRequester = remember { FocusRequester() }
                    LaunchedEffect(Unit) { focusRequester.requestFocus() }
                    BasicTextField(
                        value = draft,
                        onValueChange = { if (it.length <= NAME_MAX) onDraftChange(it) },
                        singleLine = true,
                        textStyle = NAME_STYLE.copy(color = HaloText),
                        cursorBrush = SolidColor(HaloRed),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onCommitEdit() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    )
                } else {
                    Text(
                        entry.lapse.name,
                        color = HaloText,
                        style = NAME_STYLE,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            MonoLabel(tag, color = HaloFaint)
        }

        // ---------- croix / confirmation de suppression ----------
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .then(
                    if (isConfirmingDelete || canDelete) {
                        Modifier.clickable { if (isConfirmingDelete) onConfirmDelete() else onArmDelete() }
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isConfirmingDelete) {
                CheckGlyph(color = HaloRed)
            } else {
                val color = if (canDelete) HaloMuted else HaloFaint
                Canvas(Modifier.size(11.dp)) {
                    val sw = 1.5.dp.toPx()
                    drawLine(color, Offset(0f, 0f), Offset(size.width, size.height), sw, StrokeCap.Round)
                    drawLine(color, Offset(size.width, 0f), Offset(0f, size.height), sw, StrokeCap.Round)
                }
            }
        }
    }
}

/** Un nom de lapse tient sur la ligne d'une carte, et sur la matrice il n'y a
 *  même pas la place de l'écrire. Vingt-quatre signes est déjà généreux. */
private const val NAME_MAX = 24

/** Le ✓ de confirmation d'une suppression, en accent. */
@Composable
private fun CheckGlyph(color: Color) {
    Canvas(Modifier.size(12.dp)) {
        val sw = 1.7.dp.toPx()
        drawLine(color, Offset(size.width * 0.05f, size.height * 0.5f), Offset(size.width * 0.4f, size.height * 0.85f), sw, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.4f, size.height * 0.85f), Offset(size.width * 0.98f, size.height * 0.12f), sw, StrokeCap.Round)
    }
}
