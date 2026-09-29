package com.android.mrzcardreader

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.mrzcardreader.cardconnectors.CardConnector
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PassportOcrInstrumentedTest {
    @Test
    fun recognizesAndParsesProvidedPassportImage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val imageFile = File(context.filesDir, "ekyc_image.jpg")
        val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
        assertNotNull("Test image was not copied to ${imageFile.absolutePath}", bitmap)

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val result = Tasks.await(
            recognizer.process(InputImage.fromBitmap(bitmap, 0)),
            30,
            TimeUnit.SECONDS
        )
        recognizer.close()

        val lines = result.textBlocks.flatMap { block -> block.lines.map { it.text } }
        println("OCR_LINES=${lines.joinToString(" | ")}")
        val card = CardConnector.parsePassport(lines)

        assertNotNull("Parser rejected OCR lines: $lines", card)
        assertEquals("K0521119E", card?.documentNo)
        assertEquals("SGP", card?.nationality)
    }
}
