package app.glassworks.patches.tidal.navbar

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.StringComparisonType
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

private const val MODIFIER = "Landroidx/compose/ui/Modifier;"
private const val SHAPE = "Landroidx/compose/ui/graphics/Shape;"
private const val BACKGROUND_KT = "Landroidx/compose/foundation/BackgroundKt;"
private const val TAB_TYPE = "Lcom/tidal/android/feature/appscaffold/ui/TabBarContract\$TabType;"

/**
 * The AppScaffold composable that lays out the bottom area:
 * a gradient box, and inside it one rounded card holding the mini player and the tab bar.
 *
 * Compose trace strings keep source names, so they survive obfuscation. The line number is left
 * out so the fingerprint keeps matching when TIDAL edits AppScaffold.kt.
 *
 * Instruction matches:
 * 0. Trace name.
 * 1. `Modifier.background(brush)`: the transparent-to-black gradient behind the card.
 * 2. `Modifier.background(color, shape)`: the card's solid surface color.
 * 3. `Modifier.clip(shape)`: the card's clip, right after its background.
 * 4. `AnimatedVisibility(...)`: shows and hides the mini player inside the card.
 */
internal object AppScaffoldBottomBarFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        string(
            "com.tidal.android.feature.appscaffold.ui.composable.AppScaffold (AppScaffold.kt:",
            StringComparisonType.STARTS_WITH,
        ),
        methodCall(
            definingClass = BACKGROUND_KT,
            name = "background\$default",
            parameters = listOf(
                MODIFIER,
                "Landroidx/compose/ui/graphics/Brush;",
                SHAPE,
                "F",
                "I",
                "Ljava/lang/Object;",
            ),
            returnType = MODIFIER,
        ),
        methodCall(
            definingClass = BACKGROUND_KT,
            name = "background-bw27NRU",
            parameters = listOf(MODIFIER, "J", SHAPE),
            returnType = MODIFIER,
        ),
        methodCall(
            definingClass = "Landroidx/compose/ui/draw/ClipKt;",
            name = "clip",
            parameters = listOf(MODIFIER, SHAPE),
            returnType = MODIFIER,
            // Skips only the move-result-object of the background call.
            location = MatchAfterWithin(2),
        ),
        methodCall(
            definingClass = "Landroidx/compose/animation/AnimatedVisibilityKt;",
            name = "AnimatedVisibility",
        ),
    ),
)

/**
 * The TabBar row composable: a selectable group of tab items, clipped to a circle shape.
 * Its last parameter is the Modifier that the patch wraps in a frosted pill.
 */
internal object TabBarRowFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf(
        "I",
        "I",
        "L",
        "L",
        "Landroidx/compose/runtime/Composer;",
        MODIFIER,
    ),
    filters = listOf(
        string(
            "com.tidal.android.feature.appscaffold.ui.composable.TabBar (TabBar.kt:",
            StringComparisonType.STARTS_WITH,
        ),
        methodCall(
            definingClass = "Landroidx/compose/foundation/selection/SelectableGroupKt;",
            name = "selectableGroup",
        ),
    ),
)

/**
 * The TabBar composable that builds the ordered list of tab types (`TabBarContract$TabType`,
 * not obfuscated) and turns it into tab items. It is the only TabBar function that reads the
 * UPLOADS tab type directly.
 */
internal object TabBarContentFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf(
        "L",
        "L",
        MODIFIER,
        "Landroidx/compose/runtime/Composer;",
        "I",
    ),
    filters = listOf(
        string(
            "com.tidal.android.feature.appscaffold.ui.composable.TabBar (TabBar.kt:",
            StringComparisonType.STARTS_WITH,
        ),
        fieldAccess(
            opcode = Opcode.SGET_OBJECT,
            definingClass = TAB_TYPE,
            name = "UPLOADS",
        ),
    ),
)

/** Marks the navigation bar feature as present for the settings page. */
internal object NavigationBarFeatureFingerprint : Fingerprint(
    definingClass = "Lapp/glassworks/extension/tidal/settings/Features;",
    name = "navigationBar",
    returnType = "Z",
    parameters = listOf(),
)

/**
 * The MiniPlayer row composable. It creates the artwork's rounded corner shape once, with
 * `RoundedCornerShape(radius.dp)`, and uses it for the artwork's background, border and clip.
 */
internal object MiniPlayerRowFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        string(
            "com.tidal.android.feature.appscaffold.ui.composable.MiniPlayer (MiniPlayer.kt:",
            StringComparisonType.STARTS_WITH,
        ),
        methodCall(
            definingClass = "Landroidx/compose/foundation/shape/RoundedCornerShapeKt;",
            name = "RoundedCornerShape-0680j_4",
            parameters = listOf("F"),
        ),
    ),
)
