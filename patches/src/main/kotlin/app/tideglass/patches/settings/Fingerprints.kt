package app.tideglass.patches.settings

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
import com.android.tools.smali.dexlib2.Opcode

/**
 * `MainActivity.onCreate(Bundle)`. The class name is not obfuscated.
 */
internal object MainActivityOnCreateFingerprint : Fingerprint(
    definingClass = "Lcom/aspiro/wamp/MainActivity;",
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;"),
)

/**
 * The provider of the main settings page rows. Its `a()` method builds the list of rows,
 * starting with the (not obfuscated) offline mode row.
 */
internal object MainSettingsListFingerprint : Fingerprint(
    returnType = "Ljava/util/List;",
    parameters = listOf(),
    filters = listOf(
        fieldAccess(
            opcode = Opcode.IGET_OBJECT,
            definingClass = "this",
            type = "Lcom/aspiro/wamp/settings/items/profile/SettingsItemOfflineMode;",
        ),
    ),
)
