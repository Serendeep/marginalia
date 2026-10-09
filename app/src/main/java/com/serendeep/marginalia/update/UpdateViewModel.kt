package com.serendeep.marginalia.update

import android.content.Context
import androidx.lifecycle.ViewModel
import com.serendeep.marginalia.BuildConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val manager: UpdateManager,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val enabled = BuildConfig.UPDATES_ENABLED
    val versionName: String = manager.installedVersionName
    val status: StateFlow<UpdateStatus> = manager.status
    val settings: StateFlow<UpdateSettings> = manager.settings
    val remote: StateFlow<RemoteConfig> = manager.remote.config
    val dismissedMessage: StateFlow<String?> = manager.remote.dismissed

    val channel: StateFlow<UpdateChannel> = manager.channel
    val installedChannel: UpdateChannel = manager.installedChannel

    fun setChannel(next: UpdateChannel) = manager.setChannel(next)

    val whatsNewUntil: StateFlow<Long> = manager.whatsNewUntil

    fun whatsNew(): List<VersionNotes> = manager.whatsNew()

    fun changelog(): List<VersionNotes> = manager.changelog()

    val releasesUrl: String get() = manager.releasesUrl

    fun dismissWhatsNew() = manager.dismissWhatsNew()

    val installedVersionCode: Long = manager.installedVersionCode

    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun checkNow() = manager.checkNow()

    fun download() = manager.enqueueDownload(userInitiated = true)

    fun install() = manager.installNow()

    fun dismissMessage(text: String) = manager.remote.dismiss(text)

    fun setSettings(transform: (UpdateSettings) -> UpdateSettings) = manager.updateSettings(transform)
}
