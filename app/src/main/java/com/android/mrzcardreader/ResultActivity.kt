package com.android.mrzcardreader

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.android.mrzcardreader.camera.models.IdData
import com.android.mrzcardreader.databinding.ActivityResultBinding

class ResultActivity : AppCompatActivity() {
    private lateinit var binding: ActivityResultBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
        binding.doneButton.setOnClickListener { finish() }
    }

    companion object {
        private const val EXTRA_CARD = "card"

        fun newIntent(context: Context, card: IdData): Intent =
            Intent(context, ResultActivity::class.java).putExtra(EXTRA_CARD, card)
    }
}
