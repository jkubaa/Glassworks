package app.tideglass.patches.settings

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.tideglass.patches.shared.insertBeforeKeepingLabels
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val SETTINGS_HOOK = "Lapp/tideglass/extension/settings/SettingsHook;"

/**
 * Initializes the Tideglass settings and adds a "Tideglass" page to TIDAL's settings.
 * Not shown in patch lists; patches with settings depend on it.
 */
internal val settingsPatch = bytecodePatch {
    extendWith("extensions/extension.mpe")

    execute {
        // Give the extension a Context and the current Activity before any UI is built.
        MainActivityOnCreateFingerprint.method.apply {
            val activityRegister = implementation!!.registerCount - parameters.size - 1
            addInstruction(
                0,
                "invoke-static/range { v$activityRegister .. v$activityRegister }, " +
                    "$SETTINGS_HOOK->onMainActivityCreate(Landroid/app/Activity;)V",
            )
        }

        // Add the Tideglass row before every return of the main settings list.
        MainSettingsListFingerprint.method.apply {
            val returnIndices = instructions
                .withIndex()
                .filter { (_, instruction) -> instruction.opcode == Opcode.RETURN_OBJECT }
                .map { (index, _) -> index }
                .reversed()

            if (returnIndices.isEmpty()) {
                throw PatchException("No return in the main settings list provider")
            }

            returnIndices.forEach { index ->
                val listRegister = getInstruction<OneRegisterInstruction>(index).registerA
                insertBeforeKeepingLabels(
                    index,
                    """
                        invoke-static/range { v$listRegister .. v$listRegister }, $SETTINGS_HOOK->addSettingsItem(Ljava/util/List;)Ljava/util/List;
                        move-result-object v$listRegister
                    """,
                )
            }
        }
    }
}
