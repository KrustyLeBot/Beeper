package app.portix.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.portix.data.Account
import app.portix.data.WebAddress
import app.portix.suite.ConfirmDialog
import app.portix.suite.SettingsHint

/** Adds an account ([account] null, address prefilled with [initialUrl]) or edits one, with its data and removal actions. */
@Composable
fun AccountDialog(
    account: Account?,
    initialUrl: String = "",
    onDismiss: () -> Unit,
    onSave: (Account) -> Unit,
    onRemove: () -> Unit = {},
    onClear: () -> Unit = {},
) {
    var address by remember { mutableStateOf(account?.url ?: initialUrl) }
    var name by remember { mutableStateOf(account?.name.orEmpty()) }
    var desktop by remember { mutableStateOf(account?.desktop ?: false) }
    var linkDefault by remember { mutableStateOf(account?.linkDefault ?: false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<String?>(null) }

    fun save() {
        val url = WebAddress.normalize(address)
        if (url == null) {
            error = "Adresse invalide (exemple : instagram.com)"
            return
        }
        val base = account ?: Account(Account.newId(), "", url)
        onSave(base.copy(name = name.trim().ifEmpty { WebAddress.defaultName(url) }, url = url, desktop = desktop, linkDefault = linkDefault))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (account == null) "Ajouter un compte" else "Modifier ${account.name}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    address, { address = it; error = null },
                    label = { Text("Adresse") },
                    placeholder = { Text("instagram.com") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    name, { name = it },
                    label = { Text("Nom de l'onglet") },
                    placeholder = { Text(WebAddress.normalize(address)?.let { WebAddress.defaultName(it) } ?: "Instagram") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Toggle("Version ordinateur", "Pour un réseau dont la version mobile est moins pratique.", desktop) { desktop = it }
                Toggle(
                    "Ouvrir les liens ici",
                    "Si tu as deux comptes du même réseau, celui qui reçoit les liens.", linkDefault,
                ) { linkDefault = it }
                SettingsHint("Pubs, traqueurs et cookies : uBlock Origin et Consent-O-Matic, réglables site par site depuis le menu de l'onglet.")
                if (account != null) {
                    HorizontalDivider()
                    TextButton(onClick = { confirm = "clear" }) { Text("Effacer cookies et données") }
                    TextButton(
                        onClick = { confirm = "remove" },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("Supprimer le compte") }
                }
            }
        },
        confirmButton = { TextButton(onClick = ::save) { Text(if (account == null) "Ajouter" else "Enregistrer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )

    if (account != null) when (confirm) {
        "clear" -> ConfirmDialog(
            "Effacer les données de ${account.name} ?",
            "Tu seras déconnecté de ce compte. Les autres ne sont pas touchés.",
            "Effacer", onConfirm = onClear, onDismiss = { confirm = null }, destructive = true,
        )
        "remove" -> ConfirmDialog(
            "Supprimer ${account.name} ?",
            "L'onglet disparaît de tous tes appareils synchronisés. Les cookies et la connexion de ce compte sont effacés de ce téléphone.",
            "Supprimer", onConfirm = onRemove, onDismiss = { confirm = null }, destructive = true,
        )
    }
}

@Composable
private fun Toggle(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            SettingsHint(hint)
        }
        Switch(checked, onChange)
    }
}
