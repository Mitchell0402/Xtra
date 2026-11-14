package com.github.andreyasadchy.xtra.ui.settings

import android.Manifest
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.ext.SdkExtensions
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.LaunchedEffect
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.os.LocaleListCompat
import com.github.andreyasadchy.xtra.BuildConfig
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.VideoSwap
import com.github.andreyasadchy.xtra.player.ads.AdStats
import com.github.andreyasadchy.xtra.ui.common.IntegrityDialog
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonTheme
import com.github.andreyasadchy.xtra.ui.login.LoginActivity
import com.github.andreyasadchy.xtra.ui.settings.model.ChoiceSetting
import com.github.andreyasadchy.xtra.ui.settings.model.SettingAction
import com.github.andreyasadchy.xtra.ui.settings.model.StoredSetting
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.applyTheme
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.tokenPrefs
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.chromium.net.CronetProvider
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Settings (plan 7.7, U3): one Compose screen in place of the Preference fragments, the drag-list dialogs, the search
 * fragment and the three list editors. The activity stays the entry point the rest of the app starts for a result:
 * RESULT_OK asks the main screen to rebuild, [EXTRA_ACCOUNT_CHANGED] to restart (login or logout from here).
 */
class SettingsActivity : AppCompatActivity() {

    private val viewModel: SettingsViewModel by viewModels { SettingsViewModel.SettingsViewModelFactory }
    private val customProxyViewModel: CustomProxySettingsViewModel by viewModels { CustomProxySettingsViewModel.CustomProxySettingsViewModelFactory }
    private val streamProxyViewModel: StreamProxySettingsViewModel by viewModels { StreamProxySettingsViewModel.StreamProxySettingsViewModelFactory }
    private val videoSwapViewModel: VideoSwapSettingsViewModel by viewModels { VideoSwapSettingsViewModel.VideoSwapSettingsViewModelFactory }

    private var changed = false
    private var accountChanged = false
    private val messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    private lateinit var store: SettingsStore
    private val updatePrompt = UpdatePromptState()

    private val backupLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let {
                viewModel.backupSettings(it.toString())
                messages.tryEmit(getString(R.string.st_backup_started))
            }
        }
    }

    private val restoreLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val list = mutableListOf<String>()
            result.data?.clipData?.let { clipData ->
                for (i in 0 until clipData.itemCount) {
                    clipData.getItemAt(i).uri?.let { list.add(it.toString()) }
                }
            } ?: result.data?.data?.let { list.add(it.toString()) }
            if (list.isNotEmpty()) {
                messages.tryEmit(getString(R.string.st_restore_started))
                viewModel.restoreSettings(
                    list = list,
                    networkLibrary = prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP),
                    gqlHeaders = TwitchApiHelper.getGQLHeaders(this, true),
                    helixHeaders = TwitchApiHelper.getHelixHeaders(this),
                )
            }
        }
    }

    private val loginLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        // the login page clears the old tokens as soon as it opens (v0.1), so coming back from it changed the account
        // when the user was logged in, even without a new login
        if (result.resultCode == RESULT_OK || wasLoggedIn) {
            accountChanged = true
            publishResult()
            finish()
        }
    }
    private var wasLoggedIn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState?.getBoolean(KEY_CHANGED) == true) {
            changed = true
            publishResult()
        }
        applyTheme()
        enableEdgeToEdge()
        store = SettingsStore(prefs(), tokenPrefs()) {
            changed = true
            publishResult()
        }
        val lists = SettingsLists(
            customVm = customProxyViewModel,
            streamVm = streamProxyViewModel,
            swapVm = videoSwapViewModel,
            networkLibrary = { prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP) },
            defaultSwap = {
                VideoSwap(
                    platform = prefs().getString(C.TOKEN_PLATFORM, "web"),
                    playerType = prefs().getString(C.TOKEN_PLAYER_TYPE, "site"),
                    position = -1,
                )
            },
            saveDefaultSwap = { platform, playerType ->
                prefs().edit {
                    putString(C.TOKEN_PLATFORM, platform)
                    putString(C.TOKEN_PLAYER_TYPE, playerType)
                }
            },
            requestLocalNetwork = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED
            ) {
                { ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_LOCAL_NETWORK), 1) }
            } else null,
        )
        val host = Host(environment(), lists)
        setContent {
            LagoonTheme {
                val nav = rememberSettingsNav()
                SettingsApp(store, host, nav)
                UpdatePrompt(
                    updatePrompt,
                    onUpdate = ::startUpdate,
                    onLater = {},
                    onCancelDownload = { viewModel.updateJob?.cancel() },
                )
                LaunchedEffect(Unit) {
                    viewModel.updateUrl.collect { url ->
                        if (url != null) offerUpdate(url) else messages.emit(getString(R.string.no_updates_found))
                    }
                }
                LaunchedEffect(Unit) { viewModel.updateProgress.collect { updatePrompt.downloaded = it.toLong() } }
                LaunchedEffect(Unit) { viewModel.closeUpdateDialog.collect { updatePrompt.close() } }
            }
        }
    }

    override fun onDestroy() {
        store.close()
        super.onDestroy()
    }

    private fun environment(): SettingsEnvironment {
        val httpEngine = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.S) >= 7
        val cronet = runCatching { CronetProvider.getAllProviders(this).any { it.isEnabled } }.getOrDefault(false)
        val translate = Build.SUPPORTED_64_BIT_ABIS.firstOrNull() == "arm64-v8a"
        return SettingsEnvironment(
            pictureInPicture = packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE),
            translate = translate,
            androidS = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
            networkLibraries = listOfNotNull(C.HTTP_ENGINE.takeIf { httpEngine }, C.CRONET.takeIf { cronet }, C.OKHTTP),
            translateLanguages = if (translate) {
                TranslateLanguage.getAllLanguages().map { it to Locale.forLanguageTag(it).displayLanguage }
            } else emptyList(),
            versionName = BuildConfig.VERSION_NAME,
        )
    }

    private fun account(): AccountInfo {
        val loggedIn = !TwitchApiHelper.getGQLHeaders(this, true)[C.HEADER_TOKEN].isNullOrBlank() ||
                !TwitchApiHelper.getHelixHeaders(this)[C.HEADER_TOKEN].isNullOrBlank()
        return AccountInfo(loggedIn, tokenPrefs().getString(C.USERNAME, null))
    }

    private inner class Host(override val environment: SettingsEnvironment, override val lists: SettingsLists) : SettingsHost {
        override val account: AccountInfo get() = account()
        override val messages get() = this@SettingsActivity.messages

        override fun runAction(action: SettingAction) {
            when (action) {
                SettingAction.DeleteRecentSearches -> {
                    viewModel.deleteRecentSearches()
                    this@SettingsActivity.messages.tryEmit(getString(R.string.st_done_deleted))
                }
                SettingAction.DeleteVideoPositions -> {
                    viewModel.deletePositions()
                    this@SettingsActivity.messages.tryEmit(getString(R.string.st_done_deleted))
                }
                SettingAction.ImportAppDownloads -> {
                    viewModel.importDownloads()
                    this@SettingsActivity.messages.tryEmit(getString(R.string.st_import_started))
                }
                SettingAction.Backup -> launchOrToast { backupLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)) }
                SettingAction.Restore -> launchOrToast {
                    restoreLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    })
                }
                SettingAction.CheckUpdates -> {
                    this@SettingsActivity.messages.tryEmit(getString(R.string.st_checking_updates))
                    viewModel.checkUpdates(
                        prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP),
                        prefs().getString(C.UPDATE_URL, null) ?: DEFAULT_UPDATE_URL,
                        tokenPrefs().getLong(C.UPDATE_LAST_CHECKED, 0),
                    )
                }
                SettingAction.ResetAdStats -> AdStats.reset(prefs())
                SettingAction.DeviceAdminSettings -> launchOrToast {
                    startActivity(Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.DeviceAdminSettings")))
                }
                SettingAction.IntegrityToken -> IntegrityDialog.newInstance(null).show(supportFragmentManager, null)
                SettingAction.OpenProjectPage -> launchOrToast {
                    startActivity(Intent(Intent.ACTION_VIEW, PROJECT_URL.toUri()).addCategory(Intent.CATEGORY_BROWSABLE))
                }
                // shown by the page itself
                SettingAction.AdLog, SettingAction.DownloadedLanguages -> Unit
            }
        }

        override fun afterChange(setting: StoredSetting, value: Any?) {
            when (setting.key) {
                C.UI_LANGUAGE -> AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.forLanguageTags(value?.toString()?.takeIf { it != "auto" })
                )
                C.LIVE_NOTIFICATIONS_ENABLED -> {
                    if (value == true && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ActivityCompat.checkSelfPermission(this@SettingsActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        ActivityCompat.requestPermissions(this@SettingsActivity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
                    }
                    viewModel.toggleNotifications(
                        enabled = value == true,
                        networkLibrary = prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP),
                        gqlHeaders = TwitchApiHelper.getGQLHeaders(this@SettingsActivity, true),
                        helixHeaders = TwitchApiHelper.getHelixHeaders(this@SettingsActivity),
                    )
                }
                C.SLEEP_TIMER_LOCK -> if (value == true) {
                    val devicePolicyManager = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
                    val admin = ComponentName(this@SettingsActivity, DeviceAdminReceiver::class.java)
                    if (!devicePolicyManager.isAdminActive(admin)) {
                        launchOrToast {
                            startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin))
                        }
                    }
                }
                "chatWidth" -> {
                    // v0.1: the slider stores a percentage, the chat reads the width in pixels of the longer side
                    val width = resources.displayMetrics.widthPixels
                    val height = resources.displayMetrics.heightPixels
                    val chatWidth = (maxOf(width, height) * ((value as? Int ?: 30) / 100f)).toInt()
                    prefs().edit { putInt(C.LANDSCAPE_CHAT_WIDTH, chatWidth) }
                    markChanged()
                }
                C.UPDATE_CHECK_ENABLED -> if (value == true && !prefs().getBoolean(C.UPDATE_USE_BROWSER, false)) askInstallPermission()
                C.UPDATE_USE_BROWSER -> if (value == false && prefs().getBoolean(C.UPDATE_CHECK_ENABLED, false)) askInstallPermission()
            }
        }

        override fun currentChoice(setting: ChoiceSetting): String? {
            if (setting.key != C.UI_LANGUAGE) return null
            val lang = AppCompatDelegate.getApplicationLocales()
            if (lang.isEmpty) return "auto"
            val tags = lang.toLanguageTags()
            return when {
                tags.startsWith("zh") -> "zh-CN"
                tags.startsWith("en") -> "en"
                else -> "auto"
            }
        }

        override suspend fun downloadedLanguages(): List<String> = suspendCancellableCoroutine { continuation ->
            RemoteModelManager.getInstance().getDownloadedModels(TranslateRemoteModel::class.java)
                .addOnSuccessListener { models -> continuation.resume(models.map { it.language }) }
                .addOnFailureListener { continuation.resume(emptyList()) }
        }

        override fun setDownloadedLanguages(keep: List<String>) {
            val manager = RemoteModelManager.getInstance()
            manager.getDownloadedModels(TranslateRemoteModel::class.java).addOnSuccessListener { models ->
                val downloaded = models.map { it.language }
                downloaded.filter { it !in keep }.forEach { manager.deleteDownloadedModel(TranslateRemoteModel.Builder(it).build()) }
                keep.filter { it !in downloaded }.forEach {
                    manager.download(TranslateRemoteModel.Builder(it).build(), DownloadConditions.Builder().build())
                }
            }
        }

        override fun login() {
            wasLoggedIn = account().loggedIn
            loginLauncher.launch(Intent(this@SettingsActivity, LoginActivity::class.java))
        }

        // as everywhere in the app (v0.1): the login page clears and revokes the tokens before it shows
        override fun logout() = login()

        override fun close() = finish()
    }

    private fun offerUpdate(url: String) {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.UPSIDE_DOWN_CAKE && !prefs().getBoolean(C.UPDATE_USE_BROWSER, false)) {
            askInstallPermission()
        }
        updatePrompt.offer(url)
    }

    private fun startUpdate(url: String) {
        if (prefs().getBoolean(C.UPDATE_USE_BROWSER, false)) {
            updatePrompt.close()
            launchOrToast {
                startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE))
                tokenPrefs().edit { putLong(C.UPDATE_LAST_CHECKED, System.currentTimeMillis()) }
            }
        } else {
            updatePrompt.startDownload(viewModel.updateSize)
            viewModel.downloadUpdate(prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP), url)
        }
    }

    /** v0.1 asked for the "install unknown apps" permission only on Android 14, where the installer needs it up front. */
    private fun askInstallPermission() {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.UPSIDE_DOWN_CAKE && !packageManager.canRequestPackageInstalls()) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:$packageName".toUri()))
            } catch (e: ActivityNotFoundException) {
            }
        }
    }

    private inline fun launchOrToast(block: () -> Unit) {
        try {
            block()
        } catch (e: ActivityNotFoundException) {
            messages.tryEmit(getString(R.string.no_browser_found))
        }
    }

    private fun markChanged() {
        changed = true
        publishResult()
    }

    private fun publishResult() {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_ACCOUNT_CHANGED, accountChanged))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_CHANGED, changed)
        super.onSaveInstanceState(outState)
    }

    companion object {
        const val KEY_CHANGED = "changed"
        const val EXTRA_ACCOUNT_CHANGED = "account_changed"
        const val DEFAULT_UPDATE_URL = "https://api.github.com/repos/Mitchell0402/Xtra/releases/latest"
        const val PROJECT_URL = "https://github.com/Mitchell0402/Xtra"
    }
}
