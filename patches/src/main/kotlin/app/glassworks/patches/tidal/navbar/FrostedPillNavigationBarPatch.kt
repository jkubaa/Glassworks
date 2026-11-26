package app.glassworks.patches.tidal.navbar

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.glassworks.patches.tidal.media.mediaSessionHookPatch
import app.glassworks.patches.tidal.settings.settingsPatch
import app.glassworks.patches.tidal.shared.Constants.COMPATIBILITY_TIDAL
import app.glassworks.patches.shared.argumentRegister
import app.glassworks.patches.shared.insertBeforeKeepingLabels
import app.glassworks.patches.shared.redirectStaticCall
import app.glassworks.patches.shared.returnEarly
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val EXTENSION_CLASS = "Lapp/glassworks/extension/tidal/pill/FrostedPillPatch;"
private const val ICON_BUTTON_RANK = "Lcom/squareup/ui/market/core/components/properties/IconButton\$Rank;"
private const val TAB_BAR_HOOK = "Lapp/glassworks/extension/tidal/pill/TabBarHook;"
private const val MODIFIER = "Landroidx/compose/ui/Modifier;"
private const val SHAPE = "Landroidx/compose/ui/graphics/Shape;"
private const val WRAP_MODIFIER = "($MODIFIER)$MODIFIER"

@Suppress("unused")
val frostedPillNavigationBarPatch = bytecodePatch(
    name = "Frosted pill navigation bar",
    description = "Replaces the bottom bar with frosted-glass pills for the mini player and the tabs, " +
        "with the screen content blurred behind them. Choose one or two pills, frosted or liquid glass, " +
        "their shape, size and tint (including one from the album art), and hide, reorder or separate " +
        "tabs in Settings > Glassworks.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_TIDAL)

    // The media session hook provides the current artwork for the album art tint.
    dependsOn(settingsPatch, mediaSessionHookPatch)

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

        NavigationBarFeatureFingerprint.method.returnEarly(true)

        // Replace the mini player artwork's corner shape with the one from the settings.
        MiniPlayerRowFingerprint.let {
            val method = it.method

            // The play/pause button uses the PRIMARY icon button rank (a filled white circle).
            // Let the extension swap it for SECONDARY, the plain icon the next button uses.
            // Inserted after the shape match, so the match index stays valid.
            val rankIndices = method.instructions.withIndex().filter { (_, instruction) ->
                instruction.opcode == Opcode.SGET_OBJECT &&
                    ((instruction as ReferenceInstruction).reference as FieldReference).let { field ->
                        field.definingClass == ICON_BUTTON_RANK && field.name == "PRIMARY"
                    }
            }.map { (index, _) -> index }.reversed()
            if (rankIndices.isEmpty()) {
                throw PatchException("No play button rank in the MiniPlayer composable")
            }
            if (rankIndices.any { index -> index < it.instructionMatches.last().index }) {
                throw PatchException("Unexpected play button rank before the artwork shape")
            }
            rankIndices.forEach { index ->
                val register = method.getInstruction<OneRegisterInstruction>(index).registerA
                method.addInstructions(
                    index + 1,
                    """
                        invoke-static/range { v$register .. v$register }, $EXTENSION_CLASS->playButtonRank(Ljava/lang/Object;)Ljava/lang/Object;
                        move-result-object v$register
                        check-cast v$register, $ICON_BUTTON_RANK
                    """,
                )
            }

            // Route the row's padding and the artwork and button sizes through the extension,
            // which makes them smaller for the compact mini player. The redirects keep the
            // instruction count, so the shape match index below stays valid.
            method.instructions.withIndex().filter { (_, instruction) ->
                if (instruction.opcode != Opcode.INVOKE_STATIC && instruction.opcode != Opcode.INVOKE_STATIC_RANGE) {
                    return@filter false
                }
                val reference = (instruction as ReferenceInstruction).reference as MethodReference
                reference.definingClass == "Landroidx/compose/foundation/layout/PaddingKt;" &&
                    reference.name == "padding-qDBjuR0"
            }.forEach { (index, _) ->
                method.redirectStaticCall(index, "$EXTENSION_CLASS->miniPlayerRowPadding(${MODIFIER}FFFF)$MODIFIER")
            }
            method.instructions.withIndex().filter { (_, instruction) ->
                if (instruction.opcode != Opcode.INVOKE_STATIC && instruction.opcode != Opcode.INVOKE_STATIC_RANGE) {
                    return@filter false
                }
                val reference = (instruction as ReferenceInstruction).reference as MethodReference
                reference.definingClass == "Landroidx/compose/foundation/layout/SizeKt;" &&
                    reference.name == "size-3ABfNKs"
            }.forEach { (index, _) ->
                method.redirectStaticCall(index, "$EXTENSION_CLASS->miniPlayerSize(${MODIFIER}F)$MODIFIER")
            }

            // The title and artist column's padding (closer to the play button), and their
            // marquees (edge fades while the text scrolls). The title's marquee comes first.
            method.instructions.withIndex().filter { (_, instruction) ->
                if (instruction.opcode != Opcode.INVOKE_STATIC && instruction.opcode != Opcode.INVOKE_STATIC_RANGE) {
                    return@filter false
                }
                val reference = (instruction as ReferenceInstruction).reference as MethodReference
                reference.definingClass == "Landroidx/compose/foundation/layout/PaddingKt;" &&
                    reference.name == "padding-qDBjuR0\$default"
            }.forEach { (index, _) ->
                method.redirectStaticCall(
                    index,
                    "$EXTENSION_CLASS->miniPlayerPaddingDefault(${MODIFIER}FFFFILjava/lang/Object;)$MODIFIER",
                )
            }
            val marqueeCalls = method.instructions.withIndex().filter { (_, instruction) ->
                if (instruction.opcode != Opcode.INVOKE_STATIC && instruction.opcode != Opcode.INVOKE_STATIC_RANGE) {
                    return@filter false
                }
                val reference = (instruction as ReferenceInstruction).reference as MethodReference
                reference.definingClass == "Landroidx/compose/foundation/BasicMarqueeKt;" &&
                    reference.name == "basicMarquee-1Mj1MLw\$default"
            }.map { (index, _) -> index }
            if (marqueeCalls.size != 2) {
                throw PatchException("Expected 2 marquees in the MiniPlayer composable, found ${marqueeCalls.size}")
            }
            listOf("miniPlayerTitleMarquee", "miniPlayerSubtitleMarquee").zip(marqueeCalls).forEach { (hook, index) ->
                method.redirectStaticCall(
                    index,
                    "$EXTENSION_CLASS->$hook(${MODIFIER}IIIILjava/lang/Object;FILjava/lang/Object;)$MODIFIER",
                )
            }

            // While buffering, TIDAL shows a spinner on a white circle instead of the play button.
            // Route the backgrounds (the artwork's and the circle's) and the spinner through the
            // extension, which drops the circle and makes the spinner white with the play button.
            method.instructions.withIndex().filter { (_, instruction) ->
                if (instruction.opcode != Opcode.INVOKE_STATIC && instruction.opcode != Opcode.INVOKE_STATIC_RANGE) {
                    return@filter false
                }
                val reference = (instruction as ReferenceInstruction).reference as MethodReference
                reference.definingClass == "Landroidx/compose/foundation/BackgroundKt;" &&
                    reference.name == "background-bw27NRU" &&
                    reference.parameterTypes.map { type -> type.toString() } == listOf(MODIFIER, "J", SHAPE)
            }.forEach { (index, _) ->
                method.redirectStaticCall(index, "$EXTENSION_CLASS->miniPlayerBackground(${MODIFIER}J$SHAPE)$MODIFIER")
            }
            val spinnerCalls = method.instructions.withIndex().filter { (_, instruction) ->
                if (instruction.opcode != Opcode.INVOKE_STATIC && instruction.opcode != Opcode.INVOKE_STATIC_RANGE) {
                    return@filter false
                }
                val reference = (instruction as ReferenceInstruction).reference as MethodReference
                reference.definingClass == "Landroidx/compose/material3/ProgressIndicatorKt;" &&
                    reference.name == "CircularProgressIndicator-LxG7B9w"
            }
            if (spinnerCalls.isEmpty()) {
                throw PatchException("No loading spinner in the MiniPlayer composable")
            }
            spinnerCalls.forEach { (index, _) ->
                method.redirectStaticCall(
                    index,
                    "$EXTENSION_CLASS->miniPlayerSpinner(${MODIFIER}JFJILjava/lang/Object;II)V",
                )
            }

            val shapeResultIndex = it.instructionMatches.last().index + 1
            if (method.getInstruction(shapeResultIndex).opcode != Opcode.MOVE_RESULT_OBJECT) {
                throw PatchException("No result for the artwork shape in the MiniPlayer composable")
            }
            val register = method.getInstruction<OneRegisterInstruction>(shapeResultIndex).registerA
            method.addInstructions(
                shapeResultIndex + 1,
                """
                    invoke-static/range { v$register .. v$register }, $EXTENSION_CLASS->artworkShape(Ljava/lang/Object;)Ljava/lang/Object;
                    move-result-object v$register
                    check-cast v$register, Landroidx/compose/foundation/shape/RoundedCornerShape;
                """,
            )
        }

        // Hide tabs and move the separate tab last: filter each list of tab types TIDAL builds
        // (listOf(...) calls, one per tab order).
        TabBarContentFingerprint.method.apply {
            val listResults = instructions.withIndex().filter { (index, instruction) ->
                if (instruction.opcode != Opcode.INVOKE_STATIC) return@filter false
                val reference = (instruction as ReferenceInstruction).reference as MethodReference
                reference.returnType == "Ljava/util/List;" &&
                    reference.parameterTypes.map { it.toString() } == listOf("[Ljava/lang/Object;") &&
                    getInstruction(index + 1).opcode == Opcode.MOVE_RESULT_OBJECT
            }.map { (index, _) -> index + 1 }.reversed()

            if (listResults.isEmpty()) {
                throw PatchException("No tab type list in the TabBar composable")
            }

            listResults.forEach { index ->
                val register = getInstruction<OneRegisterInstruction>(index).registerA
                addInstructions(
                    index + 1,
                    """
                        invoke-static/range { v$register .. v$register }, $TAB_BAR_HOOK->tabs(Ljava/util/List;)Ljava/util/List;
                        move-result-object v$register
                    """,
                )
            }
        }

        // Wrap the TabBar row's Modifier parameter (the last parameter) in a frosted pill.
        TabBarRowFingerprint.method.apply {
            // Give the separate tab its own layout modifier: before each TabBarItem call
            // (item, selected, onClick, modifier, composer, changed), pass the item and replace
            // the modifier. Done first, while the method's indices are still unchanged.
            val itemCalls = instructions.withIndex().filter { (_, instruction) ->
                if (instruction.opcode != Opcode.INVOKE_STATIC && instruction.opcode != Opcode.INVOKE_STATIC_RANGE) {
                    return@filter false
                }
                val reference = (instruction as ReferenceInstruction).reference as MethodReference
                val parameters = reference.parameterTypes.map { it.toString() }
                reference.returnType == "V" && parameters.size == 6 &&
                    parameters[1] == "Z" && parameters[3] == MODIFIER &&
                    parameters[4] == "Landroidx/compose/runtime/Composer;" && parameters[5] == "I"
            }.map { (index, _) -> index }.reversed()

            if (itemCalls.isEmpty()) {
                throw PatchException("No TabBarItem call in the TabBar row composable")
            }

            itemCalls.forEach { index ->
                val itemRegister = argumentRegister(index, argumentIndex = 0)
                val itemModifierRegister = argumentRegister(index, argumentIndex = 3)
                insertBeforeKeepingLabels(
                    index,
                    """
                        invoke-static/range { v$itemRegister .. v$itemRegister }, $TAB_BAR_HOOK->setCurrentItem(Ljava/lang/Object;)V
                        invoke-static/range { v$itemModifierRegister .. v$itemModifierRegister }, $TAB_BAR_HOOK->itemModifier$WRAP_MODIFIER
                        move-result-object v$itemModifierRegister
                    """,
                )
            }

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
