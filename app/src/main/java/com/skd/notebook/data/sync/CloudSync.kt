package com.skd.notebook.data.sync

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.skd.notebook.data.local.NoteDatabase
import com.skd.notebook.data.remote.FirebaseService
import com.skd.notebook.data.remote.SyncProblem
import com.skd.notebook.data.remote.SyncStatus
import com.skd.notebook.data.repository.NoteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

/**
 * Makes sure notes saved while the phone had no connection reach the cloud once it is back, even if the
 * app was closed in between:
 *  - while the app runs, the moment a network becomes available everything unconfirmed is sent;
 *  - whenever a save could not be confirmed in time, a background job is queued that WorkManager runs as soon
 *    as the phone is online again, with the app open or not.
 * Whatever is sent is only ever "notes still flagged as not confirmed" (see NoteEntity.needsSync).
 */
object CloudSync {
    private const val WORK_NAME = "cloud-upload"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val onlineState = MutableLiveData(true)

    /** Whether the phone seems to have a connection right now. */
    val online: LiveData<Boolean> = onlineState

    fun init(app: Application) {
        val manager = app.getSystemService(ConnectivityManager::class.java) ?: return
        onlineState.postValue(isOnline(manager))
        // If a phone refuses the watcher, the app still works: the background upload below does not depend on it.
        runCatching {
            manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    onlineState.postValue(true)
                    flushSoon(app)
                }

                override fun onLost(network: Network) {
                    // Another network (Wi-Fi after mobile data) may still be there.
                    onlineState.postValue(isOnline(manager))
                }
            })
        }
    }

    private fun isOnline(manager: ConnectivityManager): Boolean =
        manager.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

    fun repository(context: Context): NoteRepository {
        val db = NoteDatabase.getDatabase(context.applicationContext)
        return NoteRepository(db.noteDao(), db.folderDao(), FirebaseService()) { scheduleUpload(context) }
    }

    /** Sends everything unconfirmed, now (a no-op for a guest). */
    fun flushSoon(context: Context) {
        scope.launch {
            val repo = repository(context)
            if (repo.isSignedIn()) repo.flushUnsynced()
        }
    }

    /** Queues the background upload; it waits for a connection by itself, and runs once however often it is queued. */
    fun scheduleUpload(context: Context) {
        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }
}

/** Sends the notes and folders the cloud has not confirmed yet; WorkManager starts it when there is a connection. */
class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = CloudSync.repository(applicationContext)
        if (!repo.isSignedIn()) return Result.success() // a guest, or logged out: nothing to send
        withTimeoutOrNull(2 * 60_000L) { repo.flushUnsynced() }
        if (repo.unsyncedCountNow() == 0) return Result.success()
        // Refused (permission) will not get better by trying again soon; anything else (a dropped connection) will.
        return if (SyncStatus.state.value?.problem == SyncProblem.PERMISSION) Result.failure() else Result.retry()
    }
}
