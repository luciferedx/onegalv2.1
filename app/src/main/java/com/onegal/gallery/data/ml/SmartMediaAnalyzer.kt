package com.onegal.gallery.data.ml

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.onegal.gallery.data.model.SmartMediaScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 100% On-Device Google ML Kit media utility engine for Onegal:
 * - Live Text extraction (Latin text recognizer)
 * - QR & Barcode URL/payload detection
 * - Subject Lift foreground cutout segmentation (transparent PNG bitmap via FileProvider)
 */
class SmartMediaAnalyzer(private val context: Context) {

    private val textRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    private val barcodeScanner by lazy {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_QR_CODE,
                Barcode.FORMAT_AZTEC,
                Barcode.FORMAT_DATA_MATRIX
            )
            .build()
        BarcodeScanning.getClient(options)
    }

    private val subjectSegmenter by lazy {
        val options = SubjectSegmenterOptions.Builder()
            .enableForegroundBitmap()
            .build()
        SubjectSegmentation.getClient(options)
    }

    /**
     * Runs lightweight on-device Text & QR recognition for a settled static photo.
     */
    suspend fun scanTextAndQr(uri: Uri): SmartMediaScanResult = withContext(Dispatchers.Default) {
        val image = runCatching { InputImage.fromFilePath(context, uri) }.getOrNull()
            ?: return@withContext SmartMediaScanResult()

        val barcodes = runCatching { barcodeScanner.process(image).awaitResult() }.getOrNull().orEmpty()
        val firstBarcode = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }
        val qrUrl = firstBarcode?.url?.url?.takeIf { it.isNotBlank() }
            ?: firstBarcode?.rawValue?.takeIf {
                it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)
            }
        val qrRawValue = firstBarcode?.rawValue?.takeIf { it.isNotBlank() }

        val recognizedText = runCatching { textRecognizer.process(image).awaitResult() }
            .getOrNull()
            ?.text
            ?.trim()
            ?.takeIf { it.length >= 2 }

        SmartMediaScanResult(
            detectedText = recognizedText,
            qrUrl = qrUrl,
            qrRawValue = qrRawValue
        )
    }

    /**
     * Isolates the foreground subject as a transparent ARGB_8888 Bitmap for iOS-style Subject Lift.
     */
    suspend fun extractSubjectCutout(uri: Uri): Bitmap? = withContext(Dispatchers.Default) {
        val image = runCatching { InputImage.fromFilePath(context, uri) }.getOrNull()
            ?: return@withContext null
        val result = runCatching { subjectSegmenter.process(image).awaitResult() }.getOrNull()
            ?: return@withContext null
        result.foregroundBitmap?.copy(Bitmap.Config.ARGB_8888, false)
    }

    /**
     * Persists a transparent PNG cutout into cacheDir/cutouts and returns a shareable FileProvider URI.
     */
    suspend fun saveCutoutToCacheUri(bitmap: Bitmap, baseName: String = "onegal_cutout"): Uri? =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.cacheDir, "cutouts").apply {
                    if (!exists()) mkdirs()
                }
                // Keep cache lean by pruning stale cutouts beyond the 8 most recent files
                dir.listFiles()
                    ?.sortedByDescending { it.lastModified() }
                    ?.drop(8)
                    ?.forEach { it.delete() }

                val safePrefix = baseName.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9_-]"), "_").take(24)
                val outFile = File(dir, "${safePrefix}_${System.currentTimeMillis()}.png")
                FileOutputStream(outFile).use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                }
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
            }.getOrNull()
        }

    /**
     * Copies a transparent cutout PNG URI into the Android system clipboard for pasting into messaging apps.
     */
    suspend fun copyCutoutToClipboard(bitmap: Bitmap, baseName: String = "onegal_cutout"): Boolean {
        val uri = saveCutoutToCacheUri(bitmap, baseName) ?: return false
        return withContext(Dispatchers.Main) {
            runCatching {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    ?: return@runCatching false
                val clip = ClipData.newUri(context.contentResolver, "Onegal Cutout", uri)
                clipboard.setPrimaryClip(clip)
                true
            }.getOrDefault(false)
        }
    }

    fun close() {
        runCatching { textRecognizer.close() }
        runCatching { barcodeScanner.close() }
        runCatching { subjectSegmenter.close() }
    }

    private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result ->
            if (cont.isActive) cont.resume(result)
        }
        addOnFailureListener { error ->
            if (cont.isActive) cont.resumeWithException(error)
        }
        addOnCanceledListener {
            if (cont.isActive) cont.cancel()
        }
    }
}
