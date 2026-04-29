package soft.shadlv.twp_rewritekts

import android.os.Build
import android.os.FileObserver
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import java.io.File

class FileStateFlow(private val dir: File) {
    fun observe(fileName: String): Flow<String?> = callbackFlow {
        val observer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            object : FileObserver(dir, CLOSE_WRITE or MOVED_TO) {
                override fun onEvent(event: Int, path: String?) {
                    if (path == null) {
                        return
                    }
                    if (path == fileName) {
                        val file = File(dir, fileName)
                        val content = if (file.exists()) file.readText() else false.toString()
                        trySend(content)
                    }
                }
            }
        } else {
            @Suppress("DEPRECATION")
            object : FileObserver(dir.path, CLOSE_WRITE or MOVED_TO) {
                override fun onEvent(event: Int, path: String?) {
                    if (path == null) {
                        return
                    }
                    if (path == fileName) {
                        val file = File(dir, fileName)
                        val content = if (file.exists()) file.readText() else false.toString()
                        trySend(content)
                    }
                }
            }
        }

        val file = File(dir, fileName)
        if (file.exists()) {
            val content = if (file.exists()) file.readText() else false.toString()
            trySend(content)
        }

        observer.startWatching()

        awaitClose {
            observer.stopWatching()
        }
    }.distinctUntilChanged()
}