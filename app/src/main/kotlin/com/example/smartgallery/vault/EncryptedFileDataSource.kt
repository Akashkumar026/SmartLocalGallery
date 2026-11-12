package com.example.smartgallery.vault

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.File
import java.io.InputStream

/**
 * High-performance streaming DataSource for Media3 / ExoPlayer that decrypts AES-256-GCM
 * ciphertext chunks on-the-fly directly from private vault storage.
 * Zero unencrypted temporary file writes to disk; zero heap OOM.
 */
@UnstableApi
class EncryptedFileDataSource(
    private val context: Context,
    private val masterKey: MasterKey,
    private val resolveFile: (Uri) -> File?
) : BaseDataSource(/* isNetwork = */ false) {

    private var currentDataSpec: DataSpec? = null
    private var inputStream: InputStream? = null
    private var bytesRemaining: Long = 0
    private var isTransferOpen: Boolean = false

    override fun open(dataSpec: DataSpec): Long {
        currentDataSpec = dataSpec
        transferInitializing(dataSpec)

        val targetFile = resolveFile(dataSpec.uri)
            ?: throw java.io.FileNotFoundException("Unable to resolve private vault file for URI: ${dataSpec.uri}")

        if (!targetFile.exists()) {
            throw java.io.FileNotFoundException("Vault ciphertext file does not exist: ${targetFile.name}")
        }

        val encFile = EncryptedFile.Builder(
            context,
            targetFile,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()

        val stream = encFile.openFileInput()
        if (dataSpec.position > 0) {
            var skippedSoFar = 0L
            while (skippedSoFar < dataSpec.position) {
                val skipped = stream.skip(dataSpec.position - skippedSoFar)
                if (skipped <= 0L) {
                    // Reached end of stream prematurely or skip unfulfilled
                    break
                }
                skippedSoFar += skipped
            }
        }

        inputStream = stream
        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.length
        } else {
            val fileLen = targetFile.length()
            if (fileLen > 0L) fileLen - dataSpec.position else C.LENGTH_UNSET.toLong()
        }

        isTransferOpen = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val bytesToRead = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
            length
        } else {
            minOf(bytesRemaining, length.toLong()).toInt()
        }

        val read = inputStream?.read(buffer, offset, bytesToRead) ?: -1
        if (read == -1) {
            return C.RESULT_END_OF_INPUT
        }

        if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            bytesRemaining -= read
        }
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = currentDataSpec?.uri

    override fun close() {
        try {
            inputStream?.close()
        } finally {
            inputStream = null
            if (isTransferOpen) {
                isTransferOpen = false
                transferEnded()
            }
        }
    }
}

/**
 * Factory providing streaming EncryptedFileDataSources to ExoPlayer MediaSource pipelines.
 */
@UnstableApi
class EncryptedFileDataSourceFactory(
    private val context: Context,
    private val masterKey: MasterKey,
    private val resolveFile: (Uri) -> File?
) : DataSource.Factory {
    override fun createDataSource(): DataSource {
        return EncryptedFileDataSource(context, masterKey, resolveFile)
    }
}

