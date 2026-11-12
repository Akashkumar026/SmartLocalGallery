package com.example.smartgallery.downloader

sealed class DownloadError(open val message: String, val isRetryable: Boolean) {
    data class Network(override val message: String) : DownloadError(message, isRetryable = true)
    data class Unavailable(override val message: String) : DownloadError(message, isRetryable = false)
    data class FormatNotFound(override val message: String) : DownloadError(message, isRetryable = true)
    data class UnsupportedSite(override val message: String) : DownloadError(message, isRetryable = false)
    data class StorageFull(override val message: String) : DownloadError(message, isRetryable = true)
    data class Cancelled(override val message: String = "Download was cancelled") : DownloadError(message, isRetryable = true)
    data class Unknown(override val message: String) : DownloadError(message, isRetryable = false)

    companion object {
        fun fromThrowable(t: Throwable): DownloadError {
            val msg = t.message ?: t.localizedMessage ?: "Unknown error"
            return when {
                msg.contains("Low Storage Space", ignoreCase = true) ||
                msg.contains("ENOSPC", ignoreCase = true) ||
                msg.contains("No space left", ignoreCase = true) ||
                msg.contains("disk full", ignoreCase = true) ->
                    StorageFull(msg)

                msg.contains("Unable to download webpage", ignoreCase = true) ||
                msg.contains("Connection refused", ignoreCase = true) ||
                msg.contains("timed out", ignoreCase = true) ||
                msg.contains("Network is unreachable", ignoreCase = true) ||
                msg.contains("SocketException", ignoreCase = true) ||
                msg.contains("UnknownHostException", ignoreCase = true) ||
                msg.contains("SSLHandshakeException", ignoreCase = true) ||
                msg.contains("offline", ignoreCase = true) ->
                    Network(msg)

                msg.contains("Requested format is not available", ignoreCase = true) ||
                msg.contains("format not available", ignoreCase = true) ||
                msg.contains("bestvideo", ignoreCase = true) ||
                msg.contains("bestaudio", ignoreCase = true) ->
                    FormatNotFound(msg)

                msg.contains("Unsupported URL", ignoreCase = true) ||
                msg.contains("is not a valid URL", ignoreCase = true) ->
                    UnsupportedSite(msg)

                msg.contains("Video unavailable", ignoreCase = true) ||
                msg.contains("Private video", ignoreCase = true) ||
                msg.contains("has been removed", ignoreCase = true) ||
                msg.contains("HTTP Error 404", ignoreCase = true) ||
                msg.contains("HTTP Error 403", ignoreCase = true) ||
                msg.contains("This video requires payment", ignoreCase = true) ||
                msg.contains("login required", ignoreCase = true) ->
                    Unavailable(msg)

                msg.contains("cancel", ignoreCase = true) ||
                msg.contains("destroyProcess", ignoreCase = true) ||
                msg.contains("InterruptedException", ignoreCase = true) ->
                    Cancelled(msg)

                else -> Unknown(msg)
            }
        }

        fun fromString(errorStr: String?): DownloadError {
            if (errorStr.isNullOrBlank()) return Unknown("Unknown error")
            return when {
                errorStr.startsWith("Network:", ignoreCase = true) ->
                    Network(errorStr.substringAfter(":").trim())
                errorStr.startsWith("StorageFull:", ignoreCase = true) ->
                    StorageFull(errorStr.substringAfter(":").trim())
                errorStr.startsWith("FormatNotFound:", ignoreCase = true) ->
                    FormatNotFound(errorStr.substringAfter(":").trim())
                errorStr.startsWith("UnsupportedSite:", ignoreCase = true) ->
                    UnsupportedSite(errorStr.substringAfter(":").trim())
                errorStr.startsWith("Unavailable:", ignoreCase = true) ->
                    Unavailable(errorStr.substringAfter(":").trim())
                errorStr.startsWith("Cancelled:", ignoreCase = true) ->
                    Cancelled(errorStr.substringAfter(":").trim())
                else -> fromThrowable(RuntimeException(errorStr))
            }
        }
    }
}
