package app.vigie

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import app.vigie.ui.VigieRoot
import app.vigie.ui.VigieTheme
import app.vigie.links.Links
import app.vigie.web.Downloads
import app.vigie.web.Prompts
import app.vigie.web.Tabs

class MainActivity : ComponentActivity() {
    private val vm: VigieViewModel by viewModels()
    private lateinit var tabs: Tabs
    private var onFiles: ((List<Uri>) -> Unit)? = null

    private val storagePermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.syncOnOpen() }

    private val filePicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            val uris = when {
                result.resultCode != RESULT_OK || data == null -> emptyList()
                data.clipData != null -> List(data.clipData!!.itemCount) { data.clipData!!.getItemAt(it).uri }
                else -> listOfNotNull(data.data)
            }
            onFiles?.invoke(uris)
            onFiles = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as VigieApp
        app.extensions.onNotice = vm::show
        if (savedInstanceState == null) handleLink(intent)
        val prompts = Prompts(this) { intent, done ->
            onFiles?.invoke(emptyList())
            onFiles = done
            try {
                filePicker.launch(intent)
            } catch (e: Exception) {
                onFiles = null
                done(emptyList())
            }
        }
        tabs = Tabs(
            this, app.runtime, app.extensions, prompts, Downloads(this, lifecycleScope, vm::show),
            owners = { vm.accounts.map { it.owner } }, onRoute = vm::route, onNotice = vm::show,
        )
        setContent {
            VigieTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    VigieRoot(vm, tabs, app.extensions, app.runtime, onAllowStorage = ::askStorageAccess)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleLink(intent)
    }

    /** A network link tapped in another app, or a text shared to Vigie: opened where it belongs. */
    private fun handleLink(intent: Intent?) {
        val url = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let { Links.firstUrl(it) }
            else -> null
        } ?: return
        vm.openLink(url)
    }

    override fun onStart() {
        super.onStart()
        tabs.onStart()
        vm.syncOnOpen()
        vm.checkLinks()
        vm.updates.checkOnOpen()
        (application as VigieApp).extensions.updateIfStale()
    }

    override fun onStop() {
        tabs.onStop()
        super.onStop()
    }

    override fun onDestroy() {
        tabs.closeAll()
        super.onDestroy()
    }

    /** `Documents/Vigie` is shared with Synchrotron, hence "all files access" (a plain permission before Android 11). */
    private fun askStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))
            runCatching { startActivity(intent) }.onFailure {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            storagePermission.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
        }
    }
}
