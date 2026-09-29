package com.android.mrzcardreader

import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.android.mrzcardreader.camera.models.IdData
import com.android.mrzcardreader.databinding.ActivityMainBinding


class MainActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityMainBinding


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        if (!allPermissionsGranted()) {
            requestPermissions(REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        viewBinding.scanButton.setOnClickListener {
            openScanner()
        }

    }

    private fun openScanner() {
        if (!allPermissionsGranted()) {
            requestPermissions(REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
            return
        }

        MrzBuilder(this, this.activityResultRegistry)
            .setOnCardDetailsResponse(object : CardDetailResponse {
                override fun onCardRead(card: IdData) {
                    startActivity(ResultActivity.newIntent(this@MainActivity, card))
                }

                override fun onCardReadingCancelled() {
                    Toast.makeText(this@MainActivity, "Cancelled", Toast.LENGTH_SHORT).show()
                }

                override fun onFailed(e: Exception) {
                    Toast.makeText(this@MainActivity, e.toString(), Toast.LENGTH_SHORT).show()
                }

            })
            .start()
    }


    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS && !allPermissionsGranted()) {
            Toast.makeText(
                this,
                "Camera permission is required to scan a passport.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(android.Manifest.permission.CAMERA)

    }
}
