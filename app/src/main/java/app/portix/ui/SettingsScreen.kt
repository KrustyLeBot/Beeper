package app.portix.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.portix.PortixViewModel
import app.portix.Screen
import app.portix.PortixApp
import app.portix.data.Account
import app.portix.suite.AboutSettings
import app.portix.suite.SectionTitle
import app.portix.suite.SettingsCard
import app.portix.suite.SettingsHint
import app.portix.suite.SuiteTopBar
import app.portix.suite.SyncSettings
import app.portix.suite.UpdateSettings
import app.portix.links.Networks
import app.portix.web.Extensions
import app.portix.web.PhoneBrowser
import app.portix.web.Tabs

@Composable
fun SettingsScreen(vm: PortixViewModel, tabs: Tabs, extensions: Extensions, snackbar: SnackbarHostState) {
    var editing by remember { mutableStateOf<Account?>(null) }
    var adding by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { SuiteTopBar("Réglages", onBack = { vm.screen = Screen.Main }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SectionTitle("Comptes")
            SettingsCard {
                if (vm.accounts.isEmpty()) SettingsHint("Aucun compte. Ajoute-les un par un : rien n'est prérempli.")
                vm.accounts.forEachIndexed { i, account ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(account.name, fontWeight = FontWeight.SemiBold)
                            SettingsHint(listOfNotNull(account.host, "reçoit les liens".takeIf { account.linkDefault }, "version ordinateur".takeIf { account.desktop }).joinToString(" · "))
                        }
                        IconButton(onClick = { vm.move(i, i - 1); vm.orderChanged() }, enabled = i > 0) {
                            Icon(Icons.Default.ArrowUpward, "Monter")
                        }
                        IconButton(onClick = { vm.move(i, i + 1); vm.orderChanged() }, enabled = i < vm.accounts.lastIndex) {
                            Icon(Icons.Default.ArrowDownward, "Descendre")
                        }
                        IconButton(onClick = { editing = account }) { Icon(Icons.Default.Edit, "Modifier") }
                    }
                }
                FilledTonalButton(onClick = { if (!vm.readOnly()) adding = true }) {
                    Icon(Icons.Default.Add, null)
                    Text("Ajouter un compte", Modifier.padding(start = 8.dp))
                }
            }

            LinkSettings()
            (LocalContext.current.applicationContext as PortixApp).startup.last?.let { last ->
                SectionTitle("Dernier démarrage")
                SettingsCard {
                    SettingsHint("Depuis le lancement de l'appli : $last.")
                    SettingsHint("Le moteur Firefox démarre une fois par lancement ; « extensions » attend uBlock Origin et Consent-O-Matic.")
                }
            }
            ProtectionSettings(extensions) { title, url -> vm.extensionPage = title to url; vm.screen = Screen.Extension }
            SyncSettings(vm.syncFolder)
            UpdateSettings(vm.updates)
            AboutSettings(
                "Portix",
                "Tes réseaux sociaux dans une seule appli, sur le moteur de Firefox, sans pubs, traqueurs ni bandeaux. " +
                    "Les liens reçus s'ouvrent dans le bon compte. La liste des comptes est synchronisée ; les connexions restent sur ce téléphone.",
            )
            Spacer(Modifier.height(32.dp))
        }
    }

    if (adding) AccountDialog(null, onDismiss = { adding = false }, onSave = { if (vm.upsert(it)) adding = false })
    editing?.let { s ->
        AccountDialog(
            s,
            onDismiss = { editing = null },
            onSave = { if (vm.upsert(it)) editing = null },
            onRemove = { if (vm.remove(s)) { tabs.remove(s.id); editing = null } },
            onClear = { tabs.clearData(s); vm.show("Données de ${s.name} effacées"); editing = null },
        )
    }
}

/** The extensions that do the blocking, with their own settings pages, and Firefox's protections. */
@Composable
private fun ProtectionSettings(extensions: Extensions, open: (String, String) -> Unit) {
    SectionTitle("Protections")
    SettingsCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (spec in Extensions.SPECS) {
                val ext = extensions.installed[spec.id]
                Text(spec.title, fontWeight = FontWeight.SemiBold)
                SettingsHint(if (ext == null) "${spec.role} · pas encore installé" else "${spec.role} · version ${ext.metaData.version}")
            }
            extensions.error?.let { SettingsHint(it, MaterialTheme.colorScheme.error) }
            if (extensions.installing) LinearProgressIndicator(Modifier.fillMaxWidth())
            else OutlinedButton(onClick = { extensions.install(onlyMissing = false) }) { Text("Mettre à jour les extensions") }
        }
    }
    SettingsCard {
        Text("À faire une fois dans uBlock Origin", fontWeight = FontWeight.SemiBold)
        SettingsHint(
            "1. « Bannières d'appli » : abonne uBlock à la liste Fanboy's Mobile Notifications (bandeaux « ouvrir l'appli », " +
                "dont celui d'Amazon), absente de son catalogue. Touche « S'abonner » sur la page qui s'ouvre.",
        )
        OutlinedButton(
            onClick = { extensions.optionsUrl(Extensions.UBLOCK, Extensions.subscribePage(Extensions.MOBILE_BANNERS))?.let { open("Bannières d'appli", it) } },
            enabled = Extensions.UBLOCK.id in extensions.installed,
        ) { Text("Bannières d'appli") }
        SettingsHint(
            "2. « Listes de filtres » : coche la catégorie « Nuisances » (Annoyances) : bandeaux de cookies, widgets de chat, " +
                "pop-ups de newsletter. uBlock met ensuite toutes ses listes à jour tout seul.",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { extensions.optionsUrl(Extensions.UBLOCK, "dashboard.html#3p-filters.html")?.let { open("Listes de filtres", it) } },
                enabled = Extensions.UBLOCK.id in extensions.installed,
            ) { Text("Listes de filtres") }
            OutlinedButton(
                onClick = { extensions.optionsUrl(Extensions.UBLOCK)?.let { open("uBlock Origin", it) } },
                enabled = Extensions.UBLOCK.id in extensions.installed,
            ) { Text("Tous les réglages") }
        }
    }
    SettingsCard {
        Text("Réglages de Consent-O-Matic", fontWeight = FontWeight.SemiBold)
        SettingsHint("Toutes les catégories décochées = tout refuser, sauf les cookies nécessaires (connexion, panier).")
        OutlinedButton(
            onClick = { extensions.optionsUrl(Extensions.CONSENT)?.let { open("Consent-O-Matic", it) } },
            enabled = Extensions.CONSENT.id in extensions.installed,
        ) { Text("Ouvrir") }
    }
    SettingsCard {
        Text("Moteur Firefox", fontWeight = FontWeight.SemiBold)
        SettingsHint(
            "Chaque compte a ses propres cookies, son stockage et son cache : un réseau ne peut pas reconnaître ce que tu fais sur un autre. " +
                "Un lien vers un autre réseau ou un autre site quitte l'onglet : il s'ouvre dans le bon compte, ou dans le navigateur temporaire. " +
                "Protection renforcée contre le pistage en mode strict (traqueurs, empreinte numérique, cookies tiers isolés), " +
                "paramètres de pistage retirés des liens, signal « ne pas vendre mes données » (GPC) envoyé.",
        )
        SettingsHint("Les liens vers les applis et le Play Store sont bloqués : un réseau ne peut jamais ouvrir une autre appli.")
    }
}

/** Android's per-app "open by default" page, where the networks' addresses are ticked once. */
@Composable
private fun LinkSettings() {
    val context = LocalContext.current
    SectionTitle("Liens")
    SettingsCard {
        Text("Ouvrir les liens des réseaux avec Portix", fontWeight = FontWeight.SemiBold)
        SettingsHint(
            "Dans la page qui s'ouvre : « Ouverture liens pris en charge » activé, puis « Adr. Web prises en charge » : coche tout. " +
                "Un lien reçu (WhatsApp, SMS…) s'ouvre alors ici : dans ton compte de ce réseau s'il existe, sinon dans le navigateur temporaire.",
        )
        SettingsHint("Réseaux reconnus : " + Networks.ALL.joinToString(", ") { it.name } + ". Pour un autre lien : Partager → Portix.")
        PhoneBrowser.linkStatus(context)?.let { st ->
            SettingsHint(
                when {
                    !st.allowed -> "État : désactivé. Les liens s'ouvrent dans le navigateur."
                    st.complete -> "État : activé, ${st.total} adresses sur ${st.total} cochées."
                    else -> "État : ${st.selected} adresses cochées sur ${st.total}."
                },
                if (st.complete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
        OutlinedButton(onClick = { PhoneBrowser.openLinkSettings(context) }) { Text("Ouvrir le réglage Android") }
    }
    val taken = remember { Networks.ALL.associateWith { PhoneBrowser.claimants(context, it.home) }.filterValues { it.isNotEmpty() } }
    if (taken.isNotEmpty()) {
        SettingsCard {
            Text("Applis qui gardent des liens", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
            SettingsHint(
                "Android donne un lien à l'appli qui l'a fait valider par le réseau, et décoche alors cette adresse pour Portix. " +
                    "Désinstalle ces applis, ou dans leur page Android → « Définition par défaut », coupe « Ouverture liens pris en charge ». " +
                    "Une appli préinstallée et désactivée garde ses liens : il faut quand même lui couper ce réglage.",
            )
            taken.forEach { (network, apps) -> SettingsHint("${network.name} : ${apps.joinToString(", ")}") }
        }
    }
}
