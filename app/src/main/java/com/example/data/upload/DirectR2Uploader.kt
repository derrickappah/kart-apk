package com.example.data.upload

import com.example.data.remote.KartCursorApi
import com.example.data.remote.PresignRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

data class UploadResult(
    val objectKey: String,
    val publicUrl: String,
    val isPrivate: Boolean,
    val provider: String = "r2"
)

/**
 * Direct-to-R2 uploader using /api/uploads/presign to eliminate Vercel serverless
 * payload bottlenecks and storage origin egress.
 */
class DirectR2Uploader(
    private val api: KartCursorApi,
    private val directHttpClient: OkHttpClient
) {

    companion object {
        const val MAX_UPLOAD_BYTES = 5L * 1024L * 1024L
    }

    suspend fun uploadFile(
        file: File,
        bucket: String = "products",
        mimeType: String = "image/webp",
        conversationId: String? = null
    ): UploadResult = withContext(Dispatchers.IO) {
        require(file.exists() && file.length() in 1..MAX_UPLOAD_BYTES) {
            "File size must be between 1 byte and 5 MB"
        }
        uploadBytes(
            bytes = file.readBytes(),
            bucket = bucket,
            mimeType = mimeType,
            conversationId = conversationId
        )
    }

    suspend fun uploadBytes(
        bytes: ByteArray,
        bucket: String = "products",
        mimeType: String = "image/jpeg",
        conversationId: String? = null
    ): UploadResult = withContext(Dispatchers.IO) {
        val size = bytes.size.toLong()
        require(size in 1..MAX_UPLOAD_BYTES) {
            "File size must be between 1 byte and 5 MB"
        }

        val presign = api.requestPresignedUpload(
            PresignRequest(
                bucket = bucket,
                contentType = mimeType,
                fileSize = size,
                conversationId = conversationId
            )
        )
        check(presign.uploadUrl.isNotBlank() && presign.publicUrl.isNotBlank()) {
            "Presign endpoint returned incomplete upload URLs"
        }

        val requestBody = bytes.toRequestBody(mimeType.toMediaType())
        val putBuilder = Request.Builder()
            .url(presign.uploadUrl)
            .put(requestBody)
            .header("Content-Type", mimeType)

        presign.headers.forEach { (k, v) -> putBuilder.header(k, v) }

        directHttpClient.newCall(putBuilder.build()).execute().use { resp ->
            check(resp.isSuccessful) {
                "Direct R2 PUT upload failed with HTTP ${resp.code}"
            }
        }

        UploadResult(
            objectKey = presign.objectKey,
            publicUrl = presign.publicUrl,
            isPrivate = presign.isPrivate,
            provider = presign.provider
        )
    }
}
