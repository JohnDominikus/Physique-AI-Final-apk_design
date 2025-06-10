package com.example.physiqueaiapkfinal

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil

import com.example.physiqueaiapkfinal.R
import com.example.physiqueaiapkfinal.databinding.ActivityPoseaiBinding

class PoseActivity : ComponentActivity() {

    private lateinit var mBinding: ActivityPoseaiBinding

    private val REQUESTCODE_PERMISSIONS = 3
    private val TAG = "PoseActivity"

    private val permissionsForAndroid13Plus = arrayListOf(
        android.Manifest.permission.CAMERA,
        android.Manifest.permission.READ_MEDIA_IMAGES,
        android.Manifest.permission.READ_MEDIA_VIDEO
    )

    private val permissionsForOlderAndroid = arrayListOf(
        android.Manifest.permission.CAMERA,
        android.Manifest.permission.READ_EXTERNAL_STORAGE,
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mBinding = DataBindingUtil.setContentView(this, R.layout.activity_poseai)
//        mBinding.btnSingleMode.setOnClickListener {
//            startActivity(Intent(this, SingleImageActivity::class.java))
//        }

        mBinding.btnStreamMode.setOnClickListener {
            startActivity(Intent(this, StreamActivity::class.java))
        }

        if (!isPermissionGranted()) {
            askPendingPermission()
        }
    }

    private fun askPendingPermission() {
        val permissionToAsk = ArrayList<String>()

        // Piliin ang tamang permissions batay sa Android version
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsForAndroid13Plus
        } else {
            permissionsForOlderAndroid
        }

        // Check kung aling permissions ang kailangan
        for (permission in permissions) {
            if (!isGranted(permission)) {
                permissionToAsk.add(permission)
            }
        }

        if (permissionToAsk.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionToAsk.toTypedArray(),
                REQUESTCODE_PERMISSIONS
            )
        }
    }

    private fun isPermissionGranted(): Boolean {
        // Piliin ang tamang permissions batay sa Android version
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsForAndroid13Plus
        } else {
            permissionsForOlderAndroid
        }

        for (permission in permissions) {
            if (!isGranted(permission)) {
                return false
            }
        }
        return true
    }

    private fun isGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUESTCODE_PERMISSIONS) {
            var allGranted = true

            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false
                    break
                }
            }

            if (!allGranted) {
                Toast.makeText(
                    this,
                    "Hindi makapagpatuloy nang wala ang mga kinakailangang permissions",
                    Toast.LENGTH_LONG
                ).show()

                Log.d(TAG, "Hindi ibinigay ang lahat ng kinakailangang permissions")
            }
        }
    }
}



