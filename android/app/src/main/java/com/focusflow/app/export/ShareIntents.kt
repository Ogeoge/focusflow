package com.focusflow.app.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat

object ShareIntents {

    /**
     * Builds an ACTION_SEND chooser intent for sharing a generated CSV file.
     *
     * Caller must provide a content:// Uri (typically from a FileProvider).
     */
    fun shareCsvChooser(
        context: Context,
        csvUri: Uri,
        chooserTitle: String = "Share FocusFlow session history",
    ): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, csvUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        return Intent.createChooser(send, chooserTitle).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * Convenience helper to launch the chooser.
     */
    fun startShareCsv(
        context: Context,
        csvUri: Uri,
        chooserTitle: String = "Share FocusFlow session history",
    ) {
        ContextCompat.startActivity(
            context,
            shareCsvChooser(context = context, csvUri = csvUri, chooserTitle = chooserTitle),
            null,
        )
    }
}
