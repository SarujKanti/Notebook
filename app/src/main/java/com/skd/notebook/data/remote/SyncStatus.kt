package com.skd.notebook.data.remote

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.firestore.FirebaseFirestoreException

/** Why the cloud is not taking the notes, in terms the user can act on. */
enum class SyncProblem {
    /** The cloud refused (permission denied / not signed in): usually the Firestore security rules. */
    PERMISSION,

    /** The cloud could not be reached. */
    OFFLINE,

    /** Anything else. */
    OTHER
}

/** Null [problem] means the last thing the cloud was asked went fine. */
data class SyncState(val problem: SyncProblem? = null, val detail: String? = null)

/**
 * What the last conversation with the cloud was like. Cloud errors used to be thrown away, so a note that
 * never reached the cloud (rules refusing it, no permission) looked exactly like one that did, and was
 * lost when the user logged out. Now they are reported here, and the screens can say so.
 *
 * Reading and writing are tracked apart: a good read does not hide a failed write, and the other way round.
 */
object SyncStatus {
    private const val TAG = "SyncStatus"
    private val mutableState = MutableLiveData(SyncState())
    private var read: SyncState = SyncState()
    private var write: SyncState = SyncState()

    val state: LiveData<SyncState> = mutableState

    fun readOk() = update { read = SyncState() }

    fun writeOk() = update { write = SyncState() }

    fun readFailed(error: Throwable) = update { read = describe(error) }

    fun writeFailed(error: Throwable) = update { write = describe(error) }

    @Synchronized
    private fun update(change: () -> Unit) {
        change()
        val shown = if (write.problem != null) write else read
        if (mutableState.value != shown) mutableState.postValue(shown)
    }

    private fun describe(error: Throwable): SyncState {
        val code = (error as? FirebaseFirestoreException)?.code
        val problem = when (code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            FirebaseFirestoreException.Code.UNAUTHENTICATED -> SyncProblem.PERMISSION
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> SyncProblem.OFFLINE
            else -> SyncProblem.OTHER
        }
        Log.w(TAG, "Cloud sync problem ($problem, code=$code): ${error.message}", error)
        return SyncState(problem, error.message)
    }
}
