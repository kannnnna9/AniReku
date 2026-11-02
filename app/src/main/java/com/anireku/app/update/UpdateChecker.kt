package com.anireku.app.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.anireku.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Self-update against GitHub Releases.
 *
 * Deliberately does **no** signature pinning of its own: Android refuses to install an APK whose
 * signing certificate differs from the installed one, so a tampered download simply fails at
 * install time. What this class must get right is the version comparison and the download handoff.
 */
class UpdateChecker(
    private val context: Context,
    private val http: OkHttpClient = defaultClient(),
    private val endpoint: String = DEFAULT_ENDPOINT,
) {

    @Serializable
    data class Release(
        @SerialName("tag_name") val tag: String,
        @SerialName("name") val name: String? = null,
        @SerialName("body") val notes: String? = null,
        @SerialName("published_at") val publishedAt: String? = null,
        val prerelease: Boolean = false,
        val draft: Boolean = false,
        val assets: List<Asset> = emptyList(),
    )

    @Serializable
    data class Asset(
        val name: String,
        @SerialName("browser_download_url") val downloadUrl: String,
        val size: Long = 0,
    )

    data class Available(
        val version: String,
        val notes: String?,
        val apkUrl: String,
    )

    /** Returns [Available] when the latest release is newer than the running build, else null. */
    suspend fun check(includePrerelease: Boolean = false): Available? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(endpoint)
            .header("Accept", "application/vnd.github+json")
            .build()

        val payload = try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Update check failed: HTTP ${response.code}")
                }
                response.body?.string().orEmpty()
            }
        } catch (io: IOException) {
            throw io
        }

        val releases = json.decodeFromString(ListSerializer, payload)
        val latest = releases.firstOrNull { !it.draft && (includePrerelease || !it.prerelease) }
            ?: return@withContext null

        val remoteVersion = latest.tag.trimStart('v', 'V')
        if (compareVersions(remoteVersion, BuildConfig.VERSION_NAME) <= 0) return@withContext null

        // Prefer a universal APK; fall back to any APK asset so a renamed build still updates.
        val apk = latest.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
            ?: return@withContext null

        Available(
            version = remoteVersion,
            notes = latest.notes,
            apkUrl = apk.downloadUrl,
        )
    }

    /**
     * Queues the APK through DownloadManager and prompts the installer once it lands.
     *
     * @return the download id, so the caller can show progress or cancel.
     */
    fun download(update: Available): Long {
        val file = apkCacheFile(update.version)
        if (file.exists()) file.delete()

        val request = DownloadManager.Request(Uri.parse(update.apkUrl))
            .setTitle("AniReku ${update.version}")
            .setMimeType(APK_MIME)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, file.name)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)

        val manager = context.getSystemService(DownloadManager::class.java)
            ?: throw IOException("DownloadManager unavailable")
        return manager.enqueue(request)
    }

    /** Registers [onReady] to fire once the download with [downloadId] completes. */
    fun observeDownload(downloadId: Long, onReady: () -> Unit, onFailed: (String) -> Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != downloadId) return
                runCatching { ctx.unregisterReceiver(this) }

                val manager = ctx.getSystemService(DownloadManager::class.java) ?: return
                val query = DownloadManager.Query().setFilterById(downloadId)
                manager.query(query)?.use { cursor ->
                    if (!cursor.moveToFirst()) {
                        onFailed("Unduhan tidak ditemukan")
                        return
                    }
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        onReady()
                    } else {
                        val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                        onFailed("Unduhan gagal (kode $reason)")
                    }
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    /** Hands the downloaded APK to the system installer via a FileProvider URI. */
    fun install(version: String) {
        val file = apkCacheFile(version)
        if (!file.exists()) throw IOException("APK belum terunduh")

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(intent)
    }

    fun apkCacheFile(version: String): File {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "updates")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "anireku-$version.apk")
    }

    fun discard(version: String) {
        apkCacheFile(version).delete()
    }

    companion object {
        const val APK_MIME = "application/vnd.android.package-archive"

        private const val OWNER = "kannnnna9"
        private const val REPO = "AniReku"
        const val DEFAULT_ENDPOINT = "https://api.github.com/repos/$OWNER/$REPO/releases"

        private val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            coerceInputValues = true
        }
        private val ListSerializer = kotlinx.serialization.builtins.ListSerializer(Release.serializer())

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        /**
         * Compares dotted numeric versions, ignoring pre-release suffixes. Compared by component so
         * "0.10.0" correctly outranks "0.9.2" — string comparison would get that backwards.
         */
        fun compareVersions(a: String, b: String): Int {
            val left = a.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
            val right = b.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
            val size = maxOf(left.size, right.size)
            for (i in 0 until size) {
                val diff = (left.getOrNull(i) ?: 0) - (right.getOrNull(i) ?: 0)
                if (diff != 0) return diff.compareTo(0)
            }
            return 0
        }
    }
}
