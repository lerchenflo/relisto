package com.lerchenflo.relisto.listings.data

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.lerchenflo.relisto.listings.domain.ImagePicker
import kotlinx.coroutines.CompletableDeferred

/**
 * System photo picker with multi-selection. The result is `content://` URIs, which
 * [AndroidImageStorage] reads. The activity registers itself in `onCreate` (also after rotation).
 */
class AndroidImagePicker : ImagePicker {

    private var launcher: ActivityResultLauncher<PickVisualMediaRequest>? = null
    private var pending: CompletableDeferred<List<Uri>>? = null

    fun register(activity: ComponentActivity) {
        launcher = activity.registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_IMAGES)) { uris ->
            pending?.complete(uris)
            pending = null
        }
    }

    override suspend fun pickImages(): List<String> {
        val launcher = launcher ?: return emptyList()
        pending?.cancel()
        val result = CompletableDeferred<List<Uri>>().also { pending = it }
        launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        return result.await().map { it.toString() }
    }

    private companion object {
        const val MAX_IMAGES = 20
    }
}
