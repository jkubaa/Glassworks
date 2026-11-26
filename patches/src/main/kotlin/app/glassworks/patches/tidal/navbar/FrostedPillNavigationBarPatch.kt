package app.glassworks.patches.tidal.navbar

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.glassworks.patches.tidal.settings.settingsPatch
import app.glassworks.patches.tidal.shared.Constants.COMPATIBILITY_TIDAL
import app.glassworks.patches.shared.argumentRegister
import app.glassworks.patches.shared.insertBeforeKeepingLabels
import app.glassworks.patches.shared.redirectStaticCall

private const val EXTENSION_CLASS = "Lapp/glassworks/extension/tidal/pill/FrostedPillPatch;"
private const val MODIFIER = "Landroidx/compose/ui/Modifier;"
private const val SHAPE = "Landroidx/compose/ui/graphics/Shape;"
private const val WRAP_MODIFIER = "($MODIFIER)$MODIFIER"

@Suppress("unused")
val frostedPillNavigationBarPatch = bytecodePatch(
    name = "Frosted pill navigation bar",
    description = "Splits the bottom bar into a frosted-glass pill for the mini player " +
        "and another for the tabs, with the screen content blurred behind them. " +
        "Adjust it or switch back to the original bar in Settings > Glassworks.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_TIDAL)

    dependsOn(settingsPatch)

    extendWith("extensions/tidal.mpe")

    execute {
        AppScaffoldBottomBarFingerprint.let { fingerprint ->
            val method = fingerprint.method
            val matches = fingerprint.instructionMatches

            // Work from the last match to the first, so inserted instructions do not shift
            // the indices that are still to be patched.

            // Wrap the mini player's AnimatedVisibility modifier (3rd argument) in a frosted pill.
            val animatedVisibilityIndex = matches[4].index
            val modifierRegister = method.argumentRegister(animatedVisibilityIndex, argumentIndex = 2)
            method.insertBeforeKeepingLabels(
                animatedVisibilityIndex,
                """
                    invoke-static/range { v$modifierRegister .. v$modifierRegister }, $EXTENSION_CLASS->miniPlayerModifier$WRAP_MODIFIER
                    move-result-object v$modifierRegister
                """,
            )

            // The shared card's clip, solid background, and the gradient behind it. With the pill
            // bar enabled these are skipped; with it disabled they do what TIDAL's code did.
            method.redirectStaticCall(
                matches[3].index,
                "$EXTENSION_CLASS->cardClip($MODIFIER$SHAPE)$MODIFIER",
            )
            method.redirectStaticCall(
                matches[2].index,
                "$EXTENSION_CLASS->cardBackground(${MODIFIER}J$SHAPE)$MODIFIER",
            )
            method.redirectStaticCall(
                matches[1].index,
                "$EXTENSION_CLASS->cardBrushBackground(${MODIFIER}Landroidx/compose/ui/graphics/Brush;" +
                    "${SHAPE}FILjava/lang/Object;)$MODIFIER",
            )
        }

        // Wrap the TabBar row's Modifier parameter (the last parameter) in a frosted pill.
        TabBarRowFingerprint.method.apply {
            // The last parameter is a single-width object, so it is the method's last register.
            val modifierRegister = implementation!!.registerCount - 1
            addInstructions(
                0,
                """
                    invoke-static/range { v$modifierRegister .. v$modifierRegister }, $EXTENSION_CLASS->tabBarModifier$WRAP_MODIFIER
                    move-result-object v$modifierRegister
                """,
            )
        }
    }
}
