package wiki.asaf.wikisayit.data.commons

import kotlinx.serialization.Serializable
import wiki.asaf.wikisayit.network.ActionApiError

/** `action=upload`'s response envelope — either [upload] (even for a rejected upload) or [error]. */
@Serializable
data class UploadActionResponse(
    val upload: UploadResult? = null,
    val error: ActionApiError? = null,
)

@Serializable
data class UploadResult(
    /** "Success", or a rejection reason such as "Warning" (e.g. a file of that name already exists). */
    val result: String,
    val filename: String? = null,
)
