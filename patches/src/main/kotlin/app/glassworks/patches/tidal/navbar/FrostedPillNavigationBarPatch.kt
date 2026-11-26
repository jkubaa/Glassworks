package app.glassworks.patches.tidal.navbar

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.glassworks.patches.tidal.settings.settingsPatch
import app.glassworks.patches.tidal.shared.Constants.COMPATIBILITY_TIDAL
import app.glassworks.patches.shared.argumentRegister
import app.glassworks.patches.shared.insertBeforeKeepingLabels
import app.glassworks.patches.shared.redirectStaticCall
import app.glassworks.patches.shared.returnEarly
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val EXTENSION_CLASS = "Lapp/glassworks/extension/tidal/pill/FrostedPillPatch;"
private const val TAB_BAR_HOOK = "Lapp/glassworks/extension/tidal/pill/TabBarHook;"
private const val MODIFIER = "Landroidx/compose/ui/Modifier;"
private const val SHAPE = "Landroidx/compose/ui/graphics/Shape;"
private const val WRAP_MODIFIER = "($MODIFIER)$MODIFIER"

@Suppress("unused")
val frostedPillNavigationBarPatch = bytecodePatch(
    name = "Frosted pill navigation bar",
    description = "Replaces the bottom bar with frosted-glass pills for the mini player and the tabs, " +
        "with the screen content blurred behind them. Choose one or two pills, their shape, size and " +
        "tint, hide tabs or give one tab its own pill in Settings > Glassworks.",
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

        NavigationBarFeatureFingerprint.method.returnEarly(true)

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
