package com.lhy.yumerin

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.BitmapFactory
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.tooling.preview.Preview
import com.lhy.yumerin.domain.AppGraph
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinComponent
import java.io.File
import com.lhy.yumerin.data.background.CompanionNotifier

class MainActivity : ComponentActivity(), KoinComponent {
    private val appGraph: AppGraph by inject()
    private var pendingBackgroundEnable: (() -> Unit)? = null
    private var pendingImport: ((String) -> Unit)? = null
    private var pendingAvatar: ((String?) -> Unit)? = null
    private var pendingCardFile: ((ByteArray?) -> Unit)? = null
    private var pendingLocalModel: ((String?) -> Unit)? = null
    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val onGranted = pendingBackgroundEnable
        pendingBackgroundEnable = null
        if (granted) onGranted?.invoke()
    }
    private val createExportDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            // The export payload is kept only until the user selects a destination.
            pendingExportPayload?.let { payload ->
                contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(payload) }
            }
        }
        pendingExportPayload = null
    }
    private val openImportDocument = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val callback = pendingImport
        pendingImport = null
        if (uri != null && callback != null) {
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { callback(it.readText()) }
        }
    }
    private val pickAvatarPhoto = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val callback = pendingAvatar
        pendingAvatar = null
        callback?.invoke(uri?.let { copyToPrivate(it, "avatars", "avatar") })
    }
    private val openLocalModelDocument = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val callback = pendingLocalModel
        pendingLocalModel = null
        callback?.invoke(uri?.let { copyToPrivate(it, "model-imports", "model") })
    }
    private val openCardDocument = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val callback = pendingCardFile
        pendingCardFile = null
        if (uri == null || callback == null) return@registerForActivityResult
        val bytes = runCatching {
            contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()
        callback(bytes)
    }
    private var pendingExportPayload: String? = null
    private var initialChatId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        initialChatId = intent.getStringExtra(CompanionNotifier.EXTRA_CHAT_ID)

        setContent {
            val appIcon = remember {
                BitmapFactory.decodeResource(resources, R.drawable.yumerin_app_icon)?.asImageBitmap()
            }
            App(
                appGraph = appGraph,
                appIcon = appIcon,
                initialChatId = initialChatId,
                skipAdultConfirmationForDebug = BuildConfig.SKIP_ADULT_CONFIRMATION,
                appVersionName = BuildConfig.VERSION_NAME,
                onCheckForUpdates = { fetchLatestAppRelease() },
                onOpenExternalUrl = { url ->
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    try {
                        startActivity(intent)
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(this, "没有可打开链接的应用。", Toast.LENGTH_SHORT).show()
                    }
                },
                onBackgroundPermissionRequest = { onGranted ->
                    if (
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        pendingBackgroundEnable = onGranted
                        requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        onGranted()
                    }
                },
                onExportData = { payload ->
                    pendingExportPayload = payload
                    createExportDocument.launch("yumerin-export.json")
                },
                onImportDataRequest = { onPayload ->
                    pendingImport = onPayload
                    openImportDocument.launch(arrayOf("application/json", "text/plain"))
                },
                onPickAvatar = { onSelected ->
                    pendingAvatar = onSelected
                    pickAvatarPhoto.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onPickLocalModel = { onSelected ->
                    pendingLocalModel = onSelected
                    openLocalModelDocument.launch(arrayOf("application/octet-stream", "*/*"))
                },
                onPickCardFile = { onSelected ->
                    pendingCardFile = onSelected
                    openCardDocument.launch(arrayOf("image/png", "application/json", "*/*"))
                }
            )
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        initialChatId = intent.getStringExtra(CompanionNotifier.EXTRA_CHAT_ID)
    }

    private fun copyToPrivate(uri: Uri, directoryName: String, prefix: String): String? {
        val directory = File(filesDir, directoryName).apply { mkdirs() }
        val target = File(directory, "$prefix-${System.currentTimeMillis()}")
        return runCatching {
            val input = requireNotNull(contentResolver.openInputStream(uri))
            input.use { target.outputStream().use(it::copyTo) }
            target.absolutePath
        }.getOrNull()
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App(skipAdultConfirmationForDebug = true)
}
