package com.adskipper.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.adskipper.data.RuleEntity
import com.adskipper.data.RuleExport
import com.adskipper.data.toJsonModel
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class JsonExporter(private val context: Context) {

    private val gson = GsonBuilder()
        .setPrettyPrinting()
        .serializeNulls()
        .create()

    suspend fun exportToFile(rules: List<RuleEntity>, uri: Uri? = null): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val export = RuleExport(
                    appVersion = getAppVersion(),
                    rules = rules.map { it.toJsonModel() }
                )
                val json = gson.toJson(export)

                val targetUri = uri ?: run {
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val fileName = generateFileName()
                    val file = File(downloadsDir, fileName)
                    file.writeText(json, Charsets.UTF_8)
                    // Notify media scanner
                    val mediaUri = Uri.fromFile(file)
                    context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, mediaUri))
                    return@run Uri.fromFile(file)
                }

                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(json.toByteArray(Charsets.UTF_8))
                    }
                }

                Result.success(targetUri.toString())
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun exportToCache(rules: List<RuleEntity>): Result<File> {
        return withContext(Dispatchers.IO) {
            try {
                val export = RuleExport(
                    appVersion = getAppVersion(),
                    rules = rules.map { it.toJsonModel() }
                )
                val json = gson.toJson(export)

                val cacheDir = context.externalCacheDir ?: context.cacheDir
                val file = File(cacheDir, generateFileName())
                file.writeText(json, Charsets.UTF_8)

                Result.success(file)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    fun shareExportFile(file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "AdSkipper Rules")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(shareIntent, "Share Rules").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    private fun getAppVersion(): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    private fun generateFileName(): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        return "adskipper_rules_${dateFormat.format(Date())}.json"
    }
}