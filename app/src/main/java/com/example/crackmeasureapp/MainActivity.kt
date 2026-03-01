package com.example.crackmeasureapp

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.sceneform.ux.ArFragment

class MainActivity : AppCompatActivity() {

    private lateinit var arFragment: ArFragment
    private lateinit var tvMeasurement: TextView
    private lateinit var tvInstructions: TextView
    private lateinit var ivResult: ImageView
    private lateinit var btnClear: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (!org.opencv.android.OpenCVLoader.initDebug()) {
            Toast.makeText(this, "Unable to load OpenCV", Toast.LENGTH_LONG).show()
        }

        arFragment = supportFragmentManager.findFragmentById(R.id.arFragment) as ArFragment
        tvMeasurement = findViewById(R.id.tvMeasurement)
        tvInstructions = findViewById(R.id.tvInstructions)
        ivResult = findViewById(R.id.ivResult)
        btnClear = findViewById(R.id.btnClear)

        btnClear.setOnClickListener {
            ivResult.visibility = View.GONE
            btnClear.visibility = View.GONE
            tvMeasurement.text = getString(R.string.width_placeholder)
            tvInstructions.text = getString(R.string.instruction_text)
        }

        setupAR()
    }

    private fun setupAR() {
        arFragment.setOnTapArPlaneListener { hitResult: HitResult, plane: Plane, _ ->
            // Allow measuring on vertical walls or horizontal floors
            if (plane.type != Plane.Type.HORIZONTAL_UPWARD_FACING && plane.type != Plane.Type.VERTICAL) {
                return@setOnTapArPlaneListener
            }

            // Get distance from camera to the intersection point
            val distance = hitResult.distance
            
            // Capture image for processing
            captureAndProcess(distance)
        }
    }

    private fun captureAndProcess(distanceMeters: Float) {
        val view = arFragment.arSceneView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        
        tvInstructions.text = "Processing image..."
        
        PixelCopy.request(view, bitmap, { copyResult ->
            if (copyResult == PixelCopy.SUCCESS) {
                runOnUiThread {
                    // Show preview
                    ivResult.setImageBitmap(bitmap)
                    ivResult.visibility = View.VISIBLE
                    
                    // We need camera intrinsics to calculate mm per pixel
                    val frame = view.arFrame
                    var focalLengthX = 1f
                    
                    frame?.camera?.imageIntrinsics?.let { intrinsics ->
                        val focalLength = intrinsics.focalLength // [fx, fy]
                        focalLengthX = focalLength[0]
                    }

                    // Field of View math:
                    // Z (distance) / F (focal length) = Physical Size / Pixel Size
                    // Physical Size (mm) = (Distance (mm) * Pixel Size) / FocalLength
                    // So Scale (mm/pixel) = Distance(mm) / FocalLength
                    val distanceMm = distanceMeters * 1000f
                    val scaleMmPerPixel = distanceMm / focalLengthX

                    val result = CrackAnalyzer.measureCrack(bitmap, scaleMmPerPixel)
                    if (result.success) {
                        tvMeasurement.text = "Crack Width: %.2f mm".format(result.maxWidthMm)
                        result.processedImage?.let { processedBmp ->
                            ivResult.setImageBitmap(processedBmp)
                        }
                        btnClear.visibility = View.VISIBLE
                    } else {
                        tvMeasurement.text = "Analysis failed: ${result.errorMessage}"
                        btnClear.visibility = View.VISIBLE
                    }
                }
            } else {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Failed to capture image", Toast.LENGTH_SHORT).show()
                }
            }
        }, Handler(Looper.getMainLooper()))
    }
}
