package app.portix.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material.icons.filled.Cookie
import app.portix.suite.SuiteTopBar
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.VisibilityOff
import app.portix.links.Links
import app.portix.links.Target
import app.portix.web.PhoneBrowser
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import app.portix.PortixViewModel
import app.portix.R
import app.portix.Screen
import app.portix.data.Account
import app.portix.suite.SuiteBanner
import app.portix.suite.Tone
import app.portix.suite.UpdateBanner
import app.portix.web.Extensions
import app.portix.web.Tabs
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

@Composable
fun PortixRoot(vm: PortixViewModel, tabs: Tabs, extensions: Extensions, runtime: GeckoRuntime, onAllowStorage: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.notices.collect { snackbar.showSnackbar(it) } }
    // Accounts removed elsewhere (another phone, through the sync) lose their open tab too.
    LaunchedEffect(vm.accounts) {
        val gone = tabs.retain(vm.accounts.map { it.id }.toSet())
        // The account on screen vanished: its page carries on in the temporary browser instead of disappearing.
        val shown = vm.selectedKey
        val url = gone[shown]
        if (shown != null && url != null && url.startsWith("http")) {
            vm.show(
                if (vm.storeError != null) "Liste des comptes illisible : la page continue dans le navigateur temporaire"
                else "Ce compte a été supprimé sur un autre appareil : la page continue dans le navigateur temporaire",
            )
            vm.route(Target.Temporary, url)
        }
    }
    // A link (from another app, or routed out of a tab) opens once the blockers are in.
    LaunchedEffect(vm.pendingLink, extensions.ready) {
        val link = vm.pendingLink ?: return@LaunchedEffect
        if (!extensions.ready) return@LaunchedEffect
        vm.pendingLink = null
        val account = vm.accounts.firstOrNull { it.id == link.key }
        // Its account was removed in between (a sync): routed again, to another account or the temporary browser.
        if (link.key != Tabs.TEMPORARY && account == null) vm.openLink(link.url)
        else tabs.open(link.key, account, link.url)
    }
    BackHandler(enabled = vm.screen != Screen.Main) { vm.screen = if (vm.screen == Screen.Extension) Screen.Settings else Screen.Main }
    when (vm.screen) {
        Screen.Main -> MainScreen(vm, tabs, extensions, snackbar, onAllowStorage)
        Screen.Settings -> SettingsScreen(vm, tabs, extensions, snackbar)
        Screen.Extension -> vm.extensionPage?.let { (title, url) ->
            ExtensionScreen(title, url, runtime, onBack = { vm.screen = Screen.Settings })
        }
    }
    extensions.popup?.let { ExtensionPopup(it, onDismiss = extensions::closePopup) }
}

@Composable
private fun MainScreen(vm: PortixViewModel, tabs: Tabs, extensions: Extensions, snackbar: SnackbarHostState, onAllowStorage: () -> Unit) {
    val temporary = vm.selectedKey == Tabs.TEMPORARY && tabs.temporaryOpen
    val account = if (temporary) null else vm.selected
    val key = if (temporary) Tabs.TEMPORARY else account?.id
    var editing by remember { mutableStateOf<Account?>(null) }
    var adding by remember { mutableStateOf<String?>(null) }

    val activity = LocalContext.current as Activity
    // Back walks the tab's history; past its first page the app goes to the background (back to
    // WhatsApp after a link), pages kept.
    BackHandler(enabled = key != null) {
        if (key != null && tabs.state(key).canGoBack) tabs.goBack(key) else activity.moveTaskToBack(true)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.statusBarsPadding().fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (vm.accounts.isEmpty() && !tabs.temporaryOpen) {
                        Text(
                            "Portix", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f).padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
                        )
                    } else {
                        AccountTabs(vm, tabs, temporary, Modifier.weight(1f))
                    }
                    TabMenu(vm, tabs, extensions, key, account, onAdd = { adding = it }, onEdit = { editing = it })
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize().imePadding()) {
            vm.storeError?.let { SuiteBanner(it, tone = Tone.Error, title = "Liste des comptes illisible", icon = Icons.Default.ErrorOutline) }
            if (vm.syncing) SuiteBanner("Liste des comptes en lecture seule le temps de la synchro.", title = "Synchronisation", icon = Icons.Default.Sync, progress = -1f)
            if (vm.needsStorageAccess) {
                SuiteBanner("Ta liste de comptes est enregistrée dans ${vm.syncFolder}, synchronisé par Synchrotron : autorise l'accès aux fichiers.", title = "Accès aux fichiers", icon = Icons.Default.Folder) {
                    TextButton(onClick = onAllowStorage) { Text("Autoriser") }
                }
            }
            extensions.error?.let {
                SuiteBanner(it, tone = Tone.Error, title = "Protections incomplètes", icon = Icons.Default.Shield) {
                    TextButton(onClick = { extensions.install(onlyMissing = true) }) { Text("Réessayer") }
                }
            }
            if (Extensions.UBLOCK.id in extensions.installed && !extensions.setupDone) {
                SuiteBanner(
                    "Deux réglages à faire une fois : les bannières « ouvrir l'appli » et les nuisances (cookies, chats).",
                    title = "Termine la configuration de uBlock", icon = Icons.Default.Shield,
                ) {
                    TextButton(onClick = extensions::markSetupDone) { Text("C'est fait") }
                    TextButton(onClick = { vm.screen = Screen.Settings }) { Text("Configurer") }
                }
            }
            UpdateBanner(vm.updates)
            when {
                key == null -> EmptyState(onAdd = { if (!vm.readOnly()) adding = "" }, onLinks = { PhoneBrowser.openLinkSettings(activity) })
                // No page loads before the blockers are in, so none ever runs unprotected.
                !extensions.ready -> Column(
                    Modifier.weight(1f).fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("Installation de uBlock Origin et Consent-O-Matic…", textAlign = TextAlign.Center)
                }
                else -> {
                    val state = tabs.state(key)
                    if (temporary) {
                        SuiteBanner(
                            "Sans compte, rien n'est gardé : tout est oublié à la fermeture.",
                            title = "Navigateur temporaire", icon = Icons.Default.VisibilityOff,
                        ) {
                            TextButton(onClick = { tabs.closeTemporary(); vm.accounts.firstOrNull()?.let { vm.select(it.id) } }) { Text("Fermer") }
                        }
                    }
                    if (state.progress < 100) LinearProgressIndicator(progress = { state.progress / 100f }, modifier = Modifier.fillMaxWidth())
                    GeckoHost(tabs, key, account, Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }

    adding?.let { start -> AccountDialog(null, initialUrl = start, onDismiss = { adding = null }, onSave = { if (vm.upsert(it)) adding = null }) }
    editing?.let { a ->
        AccountDialog(
            a,
            onDismiss = { editing = null },
            onSave = { if (vm.upsert(it)) editing = null },
            onRemove = { if (vm.remove(a)) { tabs.remove(a.id); editing = null } },
            onClear = { tabs.clearData(a); vm.show("Données de ${a.name} effacées"); editing = null },
        )
    }
}

/** The one GeckoView of the screen; switching tabs swaps its session without reloading. */
@Composable
private fun GeckoHost(tabs: Tabs, key: String, account: Account?, modifier: Modifier) {
    AndroidView(
        factory = { GeckoView(it) },
        modifier = modifier,
        update = { view ->
            // Read so that a tab rebuilt after a crash is attached again.
            if (tabs.restarted >= 0) tabs.show(key, account, view)
        },
        onRelease = { it.releaseSession() },
    )
}

/** An extension's popup (uBlock's switch for this site and element picker, Consent-O-Matic's). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExtensionPopup(session: GeckoSession, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        AndroidView(
            // The sheet is its own window: a SurfaceView would be drawn behind it.
            factory = { GeckoView(it).apply { setViewBackend(GeckoView.BACKEND_TEXTURE_VIEW); setSession(session) } },
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f),
            onRelease = { it.releaseSession() },
        )
    }
}

/** An extension's settings page, full screen. */
@Composable
private fun ExtensionScreen(title: String, url: String, runtime: GeckoRuntime, onBack: () -> Unit) {
    val session = remember(url) { GeckoSession().apply { open(runtime); loadUri(url) } }
    DisposableEffect(session) { onDispose { session.close() } }
    Scaffold(topBar = { SuiteTopBar(title, onBack = onBack) }) { padding ->
        AndroidView(
            factory = { GeckoView(it).apply { setSession(session) } },
            modifier = Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize().imePadding(),
            onRelease = { it.releaseSession() },
        )
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit, onLinks: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(160.dp).clip(CircleShape)) {
            Image(painterResource(R.mipmap.ic_launcher_background), null, Modifier.fillMaxSize())
            Image(painterResource(R.mipmap.ic_launcher_foreground), null, Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(24.dp))
        Text("Aucun compte pour l'instant", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Ajoute tes réseaux (Instagram, TikTok…) un par un, connecté ou non. Les liens reçus s'ouvrent dans le bon compte, " +
                "les autres dans un navigateur temporaire qui oublie tout.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAdd) { Text("Ajouter un compte") }
        TextButton(onClick = onLinks) { Text("Ouvrir les liens des réseaux avec Portix") }
    }
}

/** The tab menu; [key] is the shown tab (an account id or [Tabs.TEMPORARY]), [account] null for the temporary browser. */
@Composable
private fun TabMenu(
    vm: PortixViewModel, tabs: Tabs, extensions: Extensions, key: String?, account: Account?,
    onAdd: (String) -> Unit, onEdit: (Account) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, "Menu") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (key != null) {
                val state = tabs.state(key)
                val url = state.url.ifEmpty { account?.url.orEmpty() }
                DropdownMenuItem(text = { Text("Recharger") }, leadingIcon = { Icon(Icons.Default.Refresh, null) },
                    onClick = { open = false; tabs.reload(key) })
                if (account != null) {
                    DropdownMenuItem(text = { Text("Accueil de ${account.name}") }, leadingIcon = { Icon(Icons.Default.Home, null) },
                        onClick = { open = false; tabs.home(account) })
                }
                DropdownMenuItem(text = { Text("Copier l'adresse") }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                    onClick = {
                        open = false
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Lien", url))
                    })
                if (url.startsWith("http")) {
                    DropdownMenuItem(text = { Text("Ouvrir dans le navigateur du téléphone") }, leadingIcon = { Icon(Icons.Default.OpenInBrowser, null) },
                        onClick = { open = false; PhoneBrowser.open(context, url) })
                }
                if (account != null) {
                    DropdownMenuItem(text = { Text("Modifier ${account.name}") }, leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick = { open = false; onEdit(account) })
                } else {
                    DropdownMenuItem(text = { Text("Ajouter ce site comme compte") }, leadingIcon = { Icon(Icons.Default.PersonAdd, null) },
                        // The site's home page, not the video the link pointed at.
                        onClick = { open = false; if (!vm.readOnly()) onAdd(Links.host(url)?.let { "https://$it/" }.orEmpty()) })
                }
                HorizontalDivider()
                tabs.session(key)?.let { session ->
                    DropdownMenuItem(
                        text = { Column { Text("uBlock Origin"); Text("Couper sur ce site, masquer un élément", style = MaterialTheme.typography.bodySmall) } },
                        leadingIcon = { Icon(Icons.Default.Shield, null) },
                        enabled = Extensions.UBLOCK.id in extensions.installed,
                        onClick = { open = false; extensions.openButton(Extensions.UBLOCK, session) },
                    )
                    DropdownMenuItem(
                        text = { Column { Text("Consent-O-Matic"); Text("Cookies refusés sur ce site", style = MaterialTheme.typography.bodySmall) } },
                        leadingIcon = { Icon(Icons.Default.Cookie, null) },
                        enabled = Extensions.CONSENT.id in extensions.installed,
                        onClick = { open = false; extensions.openButton(Extensions.CONSENT, session) },
                    )
                }
                HorizontalDivider()
            }
            DropdownMenuItem(text = { Text("Ajouter un compte") }, leadingIcon = { Icon(Icons.Default.Add, null) },
                onClick = { open = false; if (!vm.readOnly()) onAdd("") })
            DropdownMenuItem(text = { Text("Réglages") }, leadingIcon = { Icon(Icons.Default.Settings, null) },
                onClick = { open = false; vm.screen = Screen.Settings })
        }
    }
}

/** One tab per account, plus the temporary browser while it is open; long-press an account tab and drag it to reorder. */
@Composable
private fun AccountTabs(vm: PortixViewModel, tabs: Tabs, temporary: Boolean, modifier: Modifier) {
    val haptic = LocalHapticFeedback.current
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val widths = remember { mutableStateMapOf<String, Int>() }
    val selectedId = if (temporary) null else vm.selected?.id

    Row(modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp)) {
        vm.accounts.forEach { account ->
            key(account.id) {
                val dragging = draggingId == account.id
                val selected = account.id == selectedId
                Box(
                    Modifier
                        .onSizeChanged { widths[account.id] = it.width }
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer {
                            translationX = if (dragging) dragOffset else 0f
                            scaleX = if (dragging) 1.08f else 1f
                            scaleY = if (dragging) 1.08f else 1f
                        }
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (dragging) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .clickable { vm.select(account.id) }
                        .pointerInput(account.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingId = account.id
                                    dragOffset = 0f
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount.x
                                    val i = vm.accounts.indexOfFirst { it.id == account.id }
                                    val next = vm.accounts.getOrNull(i + 1)
                                    val prev = vm.accounts.getOrNull(i - 1)
                                    val nextWidth = next?.let { widths[it.id] } ?: 0
                                    val prevWidth = prev?.let { widths[it.id] } ?: 0
                                    // Swapping moves the tab in the row, so the finger offset shrinks by that width.
                                    if (next != null && dragOffset > nextWidth / 2f) {
                                        vm.move(i, i + 1)
                                        dragOffset -= nextWidth
                                    } else if (prev != null && dragOffset < -prevWidth / 2f) {
                                        vm.move(i, i - 1)
                                        dragOffset += prevWidth
                                    }
                                },
                                onDragEnd = { draggingId = null; dragOffset = 0f; vm.orderChanged() },
                                onDragCancel = { draggingId = null; dragOffset = 0f; vm.orderChanged() },
                            )
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            account.name,
                            maxLines = 1,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier.width(24.dp).height(3.dp)
                                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
        }
        if (tabs.temporaryOpen) {
            Row(
                Modifier.clip(RoundedCornerShape(12.dp)).clickable { vm.select(Tabs.TEMPORARY) }.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.VisibilityOff, null, Modifier.size(16.dp), tint = if (temporary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    " Temporaire", maxLines = 1, fontWeight = if (temporary) FontWeight.Bold else FontWeight.Normal,
                    color = if (temporary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(onClick = { tabs.closeTemporary(); vm.accounts.firstOrNull()?.let { vm.select(it.id) } }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, "Fermer le navigateur temporaire", Modifier.size(16.dp))
                }
            }
        }
    }
}
