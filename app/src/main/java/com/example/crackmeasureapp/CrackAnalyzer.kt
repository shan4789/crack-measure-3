package com.example.crackmeasureapp

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc

object CrackAnalyzer {

    data class MeasureResult(
        val success: Boolean,
        val maxWidthPixels: Float = 0f,
        val maxWidthMm: Float = 0f,
        val processedImage: Bitmap? = null,
        val errorMessage: String = ""
    )

    fun measureCrack(bitmap: Bitmap, scaleMmPerPixel: Float): MeasureResult {
        try {
            val src = Mat()
            Utils.bitmapToMat(bitmap, src)

            // 1. Convert to Gray
            val gray = Mat()
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)

            // 2. Blur to reduce noise
            val blurred = Mat()
            Imgproc.GaussianBlur(gray, blurred, org.opencv.core.Size(5.0, 5.0), 0.0)

            // 3. Adaptive Thresholding to separate dark cracks from lighter concrete
            val thresh = Mat()
            Imgproc.adaptiveThreshold(
                blurred, thresh, 255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY_INV, 15, 10.0
            )

            // 4. Morphological operations to clean up noises
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, org.opencv.core.Size(3.0, 3.0))
            Imgproc.morphologyEx(thresh, thresh, Imgproc.MORPH_OPEN, kernel)

            // 5. Find contours
            val contours = ArrayList<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(thresh, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

            if (contours.isEmpty()) {
                return MeasureResult(false, errorMessage = "No cracks detected.")
            }

            // Find the largest contour
            var largestContour: MatOfPoint? = null
            var maxArea = 0.0
            for (contour in contours) {
                val area = Imgproc.contourArea(contour)
                if (area > maxArea) {
                    maxArea = area
                    largestContour = contour
                }
            }

            if (largestContour == null || maxArea < 50) {
                return MeasureResult(false, errorMessage = "No significant cracks detected.")
            }

            // 6. Calculate Max Width using Distance Transform
            val crackMask = Mat.zeros(thresh.size(), CvType.CV_8UC1)
            val largestContoursList = listOf(largestContour)
            Imgproc.drawContours(crackMask, largestContoursList, -1, Scalar(255.0), Core.FILLED)

            val distTransform = Mat()
            Imgproc.distanceTransform(crackMask, distTransform, Imgproc.DIST_L2, 3)

            val minMaxLocResult = Core.minMaxLoc(distTransform)
            val maxRadiusPixels = minMaxLocResult.maxVal
            val maxWidthPixels = (maxRadiusPixels * 2).toFloat()
            
            val maxWidthMm = maxWidthPixels * scaleMmPerPixel

            // 7. Draw the result
            val center = minMaxLocResult.maxLoc
            Imgproc.circle(src, center, maxRadiusPixels.toInt(), Scalar(255.0, 0.0, 0.0, 255.0), 2)
            Imgproc.drawContours(src, largestContoursList, -1, Scalar(0.0, 255.0, 0.0, 255.0), 1)

            val resultBmp = Bitmap.createBitmap(src.cols(), src.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(src, resultBmp)

            // Cleanup
            src.release()
            gray.release()
            blurred.release()
            thresh.release()
            kernel.release()
            hierarchy.release()
            crackMask.release()
            distTransform.release()

            return MeasureResult(true, maxWidthPixels, maxWidthMm, resultBmp)

        } catch (e: Exception) {
            e.printStackTrace()
            return MeasureResult(false, errorMessage = "Error processing image: ${e.message}")
        }
    }
}
