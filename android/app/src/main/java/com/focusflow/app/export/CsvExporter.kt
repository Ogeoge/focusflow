package com.focusflow.app.export

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.focusflow.app.data.db.dao.SessionDao
import com.focusflow.app.domain.model.Session
import com.focusflow.app.domain.model.SessionType
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CSV export for completed Sessions.
 *
 * Contract invariant:
 * - One row per completed Session with columns:
 *   id,type,start_epoch_ms,end_epoch_ms,duration_ms,linked_task_id,plan_label
 * - type is one of: work, break, long_break
 */
object CsvExporter {

    data class ExportResult(
        val uri: Uri,
        val fileName: String,
        val rows: Int,
    )

    /**
     * Exports all sessions in DB to a CSV file and returns a FileProvider URI.
     *
     * Caller is responsible for launching a share intent.
     */
    suspend fun exportAllSessions(
        context: Context,
        sessionDao: SessionDao,
        fileProviderAuthority: String = context.packageName + ".fileprovider",
    ): ExportResult {
        val sessions = sessionDao.getAll().map { it.toDomain() }
        return exportSessions(
            context = context,
            sessions = sessions,
            fileProviderAuthority = fileProviderAuthority,
        )
    }

    /**
     * Exports provided sessions to a CSV file and returns a FileProvider URI.
     *
     * The CSV format is fixed by contract.
     */
    fun exportSessions(
        context: Context,
        sessions: List<Session>,
        fileProviderAuthority: String = context.packageName + ".fileprovider",
    ): ExportResult {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "focusflow_sessions_$timestamp.csv"
        val file = File(exportDir, fileName)

        file.writer(Charsets.UTF_8).use { writer ->
            writer.appendLine("id,type,start_epoch_ms,end_epoch_ms,duration_ms,linked_task_id,plan_label")
            sessions.forEach { session ->
                // Ensure contract type values and column ordering.
                val type = session.type.toContractValue()
                val linkedTaskId = session.linkedTaskId.orEmpty()
                val planLabel = session.planLabel.orEmpty()

                writer.appendLine(
                    listOf(
                        session.id,
                        type,
                        session.startEpochMs.toString(),
                        session.endEpochMs.toString(),
                        session.durationMs.toString(),
                        escapeCsv(linkedTaskId),
                        escapeCsv(planLabel),
                    ).joinToString(",")
                )
            }
        }

        val uri = FileProvider.getUriForFile(context, fileProviderAuthority, file)
        return ExportResult(uri = uri, fileName = fileName, rows = sessions.size)
    }

    private fun SessionType.toContractValue(): String = when (this) {
        SessionType.WORK -> "work"
        SessionType.BREAK -> "break"
        SessionType.LONG_BREAK -> "long_break"
    }

    /**
     * Minimal CSV escaping.
     *
     * Note: IDs and numeric columns are not escaped by design.
     * Optional string columns are escaped to keep CSV valid.
     */
    private fun escapeCsv(value: String): String {
        if (value.isEmpty()) return ""
        val mustQuote = value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')
        if (!mustQuote) return value
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }
}
