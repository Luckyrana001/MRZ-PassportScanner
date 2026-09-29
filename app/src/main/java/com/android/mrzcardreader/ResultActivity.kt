package com.android.mrzcardreader

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.android.mrzcardreader.camera.models.IdData
import com.android.mrzcardreader.databinding.ActivityResultBinding
import java.io.File

class ResultActivity : AppCompatActivity() {
    private lateinit var binding: ActivityResultBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureEdgeToEdge()
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySafeDrawingInsets()

        val card = intent.getSerializableExtra(EXTRA_CARD) as? IdData ?: run {
            finish()
            return
        }

        binding.holderNameValue.text = listOf(card.lastName, card.firstName, card.middleName)
            .filter(String::isNotBlank)
            .joinToString(" ")
        binding.documentTypeValue.text = "PASSPORT"
        binding.firstNameValue.text = card.firstName
        binding.middleNameValue.text = card.middleName
        binding.lastNameValue.text = card.lastName
        binding.genderValue.text = card.gender
        binding.documentNumberValue.text = card.documentNo
        binding.dateOfBirthValue.text = card.dateOfBirth
        binding.dateOfExpiryValue.text = card.dateOfExpiry
        if (card.placeOfIssue.isNotBlank()) {
            binding.placeOfIssueValue.text = card.placeOfIssue
        } else {
            binding.placeOfIssueLabel.visibility = View.GONE
            binding.placeOfIssueValue.visibility = View.GONE
        }
        binding.nationalIdValue.text = card.idNo
        binding.nationalityValue.text = card.nationality
        binding.mrzValue.text = card.rawMrz.ifBlank { getString(R.string.mrz_unavailable) }
        val mrzBitmap = card.mrzImagePath.takeIf(String::isNotBlank)
            ?.let(BitmapFactory::decodeFile)
        if (mrzBitmap != null) {
            binding.mrzImage.setImageBitmap(mrzBitmap)
        } else {
            binding.mrzImage.visibility = View.GONE
            binding.mrzImageLabel.visibility = View.GONE
        }

        val faceBitmap = card.faceImagePath.takeIf(String::isNotBlank)
            ?.let(BitmapFactory::decodeFile)
        if (faceBitmap != null) {
            binding.faceImage.setImageBitmap(faceBitmap)
            binding.faceImageStatus.visibility = View.GONE
        } else {
            binding.faceImage.visibility = View.GONE
            binding.faceImageStatus.setText(R.string.face_image_unavailable)
        }

        binding.shareImageButton.isEnabled = mrzBitmap != null
        binding.shareImageButton.setOnClickListener { shareScanImage(card.mrzImagePath) }
        binding.shareResultButton.setOnClickListener { shareScanResult(card) }
        binding.doneButton.setOnClickListener { finish() }
    }

    private fun shareScanImage(imagePath: String) {
        val imageFile = File(imagePath)
        if (!imageFile.isFile) {
            Toast.makeText(this, R.string.scan_image_unavailable, Toast.LENGTH_SHORT).show()
            return
        }

        val imageUri = FileProvider.getUriForFile(
            this,
            "$packageName.fileprovider",
            imageFile,
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            clipData = ClipData.newUri(contentResolver, getString(R.string.share_scan_image), imageUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_scan_image)))
    }

    private fun shareScanResult(card: IdData) {
        val holderName = listOf(card.lastName, card.firstName, card.middleName)
            .filter(String::isNotBlank)
            .joinToString(" ")
        val resultText = buildString {
            appendLine(getString(R.string.share_result_heading))
            appendLine("${getString(R.string.scanned_item)}: $holderName")
            appendLine("${getString(R.string.document_number_label)}: ${card.documentNo}")
            appendLine("${getString(R.string.date_of_birth_label)}: ${card.dateOfBirth}")
            appendLine("${getString(R.string.date_of_expiry_label)}: ${card.dateOfExpiry}")
            if (card.placeOfIssue.isNotBlank()) {
                appendLine("${getString(R.string.place_of_issue_label)}: ${card.placeOfIssue}")
            }
            appendLine("${getString(R.string.gender_label)}: ${card.gender}")
            appendLine("${getString(R.string.nationality_label)}: ${card.nationality}")
            if (card.idNo.isNotBlank()) {
                appendLine("${getString(R.string.national_id_label)}: ${card.idNo}")
            }
            if (card.rawMrz.isNotBlank()) {
                appendLine()
                appendLine(getString(R.string.full_mrz))
                append(card.rawMrz)
            }
        }
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_result_heading))
            putExtra(Intent.EXTRA_TEXT, resultText)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_scan_result)))
    }

    companion object {
        private const val EXTRA_CARD = "card"

        fun newIntent(context: Context, card: IdData): Intent =
            Intent(context, ResultActivity::class.java).putExtra(EXTRA_CARD, card)
    }
}
