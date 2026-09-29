package com.android.mrzcardreader.camera

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.android.mrzcardreader.CardResult
import com.android.mrzcardreader.camera.models.IdData
import com.android.mrzcardreader.camera.overlay.TextGraphic
import com.android.mrzcardreader.camera.overlay.TextOverlay
import com.android.mrzcardreader.cardconnectors.CardConnector
import com.android.mrzcardscanner.R
import com.android.mrzcardscanner.databinding.ActivityMrzBinding
import com.google.android.gms.tasks.Task
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.io.FileOutputStream

class ImageAnalyzer(
    private val textOverlay: TextOverlay,
    private val activityMainBinding: ActivityMrzBinding,
    private val bottomSheetBehavior: BottomSheetBehavior<*>,
    private val cardResult: CardResult
) : ImageAnalysis.Analyzer, MRZResponse {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var scannerRunning = true

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        if (!scannerRunning) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        detectImageContent(InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees))
            .addOnSuccessListener { results ->
                onSuccess(results, getImageCropRect(imageProxy))
            }
            .addOnFailureListener(::onFailure)
            .addOnCompleteListener { imageProxy.close() }
    }

    private fun onFailure(it: Exception) {
        it.printStackTrace()
    }

    private fun onSuccess(results: Text?, cropRect: Rect?) {
        results?.let { result ->

            val blocks = result.textBlocks

            if (blocks.isNotEmpty()) {
                cropRect?.let { imageRect ->

                    val lineGraphics = blocks
                        .flatMap { block -> block.lines }
                        .map { line ->
                        line to TextGraphic(
                            textOverlay.context,
                            imageRect,
                            textRect = line.boundingBox,
                            textOverlay
                        )
                    }
                    val validLineGraphics = lineGraphics.filter { it.second.isInsideFrame() }
                    val validLines = validLineGraphics.map { it.first.text }
                    val recognizedLines = lineGraphics.map { it.first.text }
                    val textGraphics = validLineGraphics
                        .filter { isMrzLine(it.first.text) }
                        .map { it.second }
                    textOverlay.add(textGraphics)

                    try {
                        if (scannerRunning && validLines.isNotEmpty()) {
                            CardConnector.onLinesCaptured(validLines, this, recognizedLines)

                        } else if (!scannerRunning) {
                            CardConnector.clear()
                        }

                    } catch (e: java.lang.Exception) {

                    }
                }
            } else {
                textOverlay.clear()
            }
        }
    }

    private fun minimizeCard() {
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    private fun maximizedCard() {
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun getImageCropRect(imageProxy: ImageProxy): Rect {
        val rotation = imageProxy.imageInfo.rotationDegrees
        if (rotation == 90 || rotation == 270) {
            return Rect(0, 0, imageProxy.height, imageProxy.width)
        }
        return Rect(0, 0, imageProxy.width, imageProxy.height)
    }

    private fun detectImageContent(image: InputImage): Task<Text> {
        return recognizer.process(image)
    }

    private fun isMrzLine(line: String): Boolean {
        val normalized = line
            .uppercase()
            .replace(" ", "")
            .filter { it.isLetterOrDigit() || it == '<' || it == '«' }
        return normalized.length >= 30 &&
            (normalized.count { it == '<' || it == '«' } >= 2 ||
                normalized.count(Char::isDigit) >= 12)
    }

    override fun cardResponse(card: IdData) {
        scannerRunning = false
        Handler(Looper.getMainLooper()).post {
            val capturedImages = savePassportImages()
            card.mrzImagePath = capturedImages.first
            card.faceImagePath = capturedImages.second
            cardResult.cardDetails(card)
        }
    }

    private fun savePassportImages(): Pair<String, String> {
        val previewBitmap = activityMainBinding.viewFinder.bitmap ?: return "" to ""
        if (textOverlay.width == 0 || textOverlay.height == 0) return "" to ""

        val scaleX = previewBitmap.width.toFloat() / textOverlay.width
        val scaleY = previewBitmap.height.toFloat() / textOverlay.height
        val left = (textOverlay.frameLeft * scaleX).toInt().coerceIn(0, previewBitmap.width - 1)
        val top = (textOverlay.frameTop * scaleY).toInt().coerceIn(0, previewBitmap.height - 1)
        val right = (textOverlay.frameRight * scaleX).toInt().coerceIn(left + 1, previewBitmap.width)
        val bottom = (textOverlay.frameBottom * scaleY).toInt().coerceIn(top + 1, previewBitmap.height)
        val croppedBitmap = Bitmap.createBitmap(
            previewBitmap,
            left,
            top,
            right - left,
            bottom - top
        )
        val timestamp = System.currentTimeMillis()
        val outputFile = File(textOverlay.context.cacheDir, "passport_$timestamp.jpg")
        FileOutputStream(outputFile).use { output ->
            croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)
        }

        // ICAO passport data pages reserve the left portion above the MRZ for the portrait.
        val faceLeft = (croppedBitmap.width * 0.04f).toInt()
        val faceTop = (croppedBitmap.height * 0.12f).toInt()
        val faceRight = (croppedBitmap.width * 0.36f).toInt()
        val faceBottom = (croppedBitmap.height * 0.72f).toInt()
        val faceBitmap = Bitmap.createBitmap(
            croppedBitmap,
            faceLeft,
            faceTop,
            faceRight - faceLeft,
            faceBottom - faceTop,
        )
        val faceFile = File(textOverlay.context.cacheDir, "passport_face_$timestamp.jpg")
        FileOutputStream(faceFile).use { output ->
            faceBitmap.compress(Bitmap.CompressFormat.JPEG, 94, output)
        }
        faceBitmap.recycle()
        if (croppedBitmap !== previewBitmap) croppedBitmap.recycle()
        return outputFile.absolutePath to faceFile.absolutePath
    }

    private fun updateCardDetails(idData: IdData) {
        scannerRunning = false
        val rootView = activityMainBinding.cardLayout.rootView
        rootView.findViewById<TextView>(R.id.fNameTv).text = idData.firstName
        rootView.findViewById<TextView>(R.id.lNameTv).text = idData.lastName
        rootView.findViewById<TextView>(R.id.nameTv).text = idData.middleName
        rootView.findViewById<TextView>(R.id.genderTv).text = idData.gender
        rootView.findViewById<TextView>(R.id.idTv).text = idData.idNo
        rootView.findViewById<TextView>(R.id.docNoTv).text = idData.documentNo
        rootView.findViewById<TextView>(R.id.docTypeTv).text = "ID"
        rootView.findViewById<TextView>(R.id.dobTv).text = idData.dateOfBirth

        rootView.findViewById<Button>(R.id.rescanBtn).setOnClickListener {
            minimizeCard()
            scannerRunning = true
        }
        rootView.findViewById<Button>(R.id.confirmBtn).setOnClickListener {
            cardResult.cardDetails(idData)
        }
    }

    override fun cardReadResponse() {
    }

    override fun failedToRead() {
    }
}
