package com.android.mrzcardreader

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.android.mrzcardreader.camera.MrzCameraManager
import com.android.mrzcardreader.camera.models.IdData
import com.android.mrzcardscanner.databinding.ActivityMrzBinding


class MainMrzActivity : AppCompatActivity(), CardResult {
    private lateinit var viewBinding: ActivityMrzBinding
    private lateinit var cameraManager: MrzCameraManager


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureEdgeToEdge(lightSystemBars = false)
        viewBinding = ActivityMrzBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
        applyScannerInsets()
        initializeCamera()
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions(REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

    }

    private fun applyScannerInsets() {
        val bottomSheet = viewBinding.cardLayout
        val initialLeft = bottomSheet.paddingLeft
        val initialTop = bottomSheet.paddingTop
        val initialRight = bottomSheet.paddingRight
        val initialBottom = bottomSheet.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(viewBinding.root) { _, windowInsets ->
            val safeInsets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )

            // Keep the preview under the system bars, but place guidance and controls safely.
            viewBinding.textOverLay.setPadding(
                safeInsets.left,
                safeInsets.top,
                safeInsets.right,
                safeInsets.bottom,
            )
            bottomSheet.setPadding(
                initialLeft + safeInsets.left,
                initialTop,
                initialRight + safeInsets.right,
                initialBottom + safeInsets.bottom,
            )
            windowInsets
        }
        ViewCompat.requestApplyInsets(viewBinding.root)
    }

    private fun initializeCamera() {
        cameraManager =
            MrzCameraManager(
                this,
                this,
                viewBinding,
                this
            )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(
                    this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    private fun startCamera() {
        cameraManager.startCamera()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(android.Manifest.permission.CAMERA)

    }

    override fun cardDetails(card: IdData) {
        runOnUiThread {
            val intent = Intent("MRZ_ACTION")
            intent.putExtra("card", card)
            setResult(505, intent)
            finish()
        }
    }
}
