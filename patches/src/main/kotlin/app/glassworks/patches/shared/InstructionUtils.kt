package app.glassworks.patches.shared

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction

/**
 * Inserts [smali] before the instruction at [index], so that branches that jump to that
 * instruction also run the inserted code.
 *
 * Inserting with addInstructions(index) would leave branch labels on the original instruction,
 * so branches would skip the inserted code. Instead, the first inserted instruction replaces the
 * original in place (keeping its labels), and the original is re-added after the inserted code.
 * Only invoke and return-object instructions are supported as the original.
 */
fun MutableMethod.insertBeforeKeepingLabels(index: Int, smali: String) {
    val original = toSmali(index)
    val lines = smali.trimIndent().lines().map { it.trim() }.filter { it.isNotEmpty() }
    if (lines.isEmpty()) return

    replaceInstruction(index, lines.first())
    addInstructions(index + 1, (lines.drop(1) + original).joinToString("\n"))
}

/**
 * @return The register holding the argument at [argumentIndex] of the invoke instruction at [index].
 * Only valid when all arguments before [argumentIndex] are single-width (not long or double).
 */
fun MutableMethod.argumentRegister(index: Int, argumentIndex: Int): Int =
    when (val instruction = getInstruction(index)) {
        is RegisterRangeInstruction -> instruction.startRegister + argumentIndex
        is FiveRegisterInstruction -> instruction.registers().getOrNull(argumentIndex)
            ?: throw PatchException("Argument index $argumentIndex out of range at index $index")

        else -> throw PatchException("Instruction at index $index is not an invoke: $instruction")
    }

/**
 * Replaces the static call at [index] with a call to [target] (`Lclass;->name(params)return`),
 * keeping the same registers. [target] must take the same parameters and return the same type.
 */
fun MutableMethod.redirectStaticCall(index: Int, target: String) {
    replaceInstruction(index, invokeSmali(index, "invoke-static", target))
}

/**
 * Smali for the invoke or return-object instruction at [index].
 */
private fun MutableMethod.toSmali(index: Int): String {
    val instruction = getInstruction(index)
    if (instruction.opcode == Opcode.RETURN_OBJECT) {
        return "return-object v${(instruction as OneRegisterInstruction).registerA}"
    }

    val reference = (instruction as? ReferenceInstruction)?.reference
        ?: throw PatchException("Unsupported instruction at index $index: ${instruction.opcode}")
    // dexlib2's Opcode.name field is the smali name, such as "invoke-static/range".
    // invokeSmali adds "/range" back for range instructions.
    val opcode = instruction.opcode.name.removeSuffix("/range")
    return invokeSmali(index, opcode, reference.toString())
}

private fun MutableMethod.invokeSmali(index: Int, opcode: String, target: String): String =
    when (val instruction = getInstruction(index)) {
        is RegisterRangeInstruction -> {
            val last = instruction.startRegister + instruction.registerCount - 1
            "$opcode/range { v${instruction.startRegister} .. v$last }, $target"
        }

        is FiveRegisterInstruction ->
            "$opcode { ${instruction.registers().joinToString { "v$it" }} }, $target"

        else -> throw PatchException("Instruction at index $index is not an invoke: $instruction")
    }

private fun FiveRegisterInstruction.registers() =
    listOf(registerC, registerD, registerE, registerF, registerG).take(registerCount)

/**
 * Makes the method return [value] immediately. For methods returning boolean, such as the
 * extension's feature flags.
 */
fun MutableMethod.returnEarly(value: Boolean) {
    addInstructions(
        0,
        """
            const/4 v0, ${if (value) "0x1" else "0x0"}
            return v0
        """,
    )
}
