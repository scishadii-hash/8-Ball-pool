package com.poolvision.training

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.min

/**
 * Offline training-image analysis only.
 * The user explicitly selects a still image; this screen does not capture or monitor other apps.
 */
class ImageAnalyzerActivity : Activity() {
    private val pickImageRequest = 4102
    private lateinit var status: TextView
    private lateinit var preview: AnalysisPreview
    private var cvReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cvReady = OpenCVLoader.initLocal()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 28, 20, 16)
            setBackgroundColor(Color.rgb(15, 17, 23))
        }
        val title = TextView(this).apply {
            text = "Training Image Analyzer"
            textSize = 23f
            setTextColor(Color.WHITE)
        }
        status = TextView(this).apply {
            text = if (cvReady) "OpenCV ready. Select a training photo to analyze."
                    else "OpenCV could not initialize. Check the build output."
            textSize = 14f
            setTextColor(Color.LTGRAY)
            setPadding(0, 12, 0, 12)
        }
        val choose = Button(this).apply {
            text = "Choose training image"
            isAllCaps = false
            setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                }
                startActivityForResult(intent, pickImageRequest)
            }
        }
        preview = AnalysisPreview(this)
        root.addView(title)
        root.addView(status)
        root.addView(choose)
        root.addView(preview, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(root)
    }

    @Deprecated("Deprecated by Android, retained for compatibility with this starter project")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != pickImageRequest || resultCode != RESULT_OK) return
        val uri: Uri = data?.data ?: return
        if (!cvReady) {
            status.text = "OpenCV is unavailable in this build."
            return
        }
        try {
            contentResolver.openInputStream(uri).use { stream ->
                val source = BitmapFactory.decodeStream(stream)
                    ?: throw IllegalArgumentException("Could not read this image.")
                val maxSide = 1600
                val scale = min(1f, maxSide.toFloat() / maxOf(source.width, source.height))
                val bitmap = if (scale < 1f) Bitmap.createScaledBitmap(
                    source, (source.width * scale).toInt().coerceAtLeast(1),
                    (source.height * scale).toInt().coerceAtLeast(1), true
                ) else source
                analyze(bitmap)
            }
        } catch (e: Exception) {
            status.text = "Could not analyze image: ${e.message ?: "unknown error"}"
        }
    }

    private fun analyze(bitmap: Bitmap) {
        val rgba = Mat()
        val rgb = Mat()
        val hsv = Mat()
        val gray = Mat()
        val mask = Mat()
        val circles = Mat()
        try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(rgb, hsv, Imgproc.COLOR_RGB2HSV)
            Imgproc.cvtColor(rgb, gray, Imgproc.COLOR_RGB2GRAY)
            Imgproc.GaussianBlur(gray, gray, Size(9.0, 9.0), 2.0)

            // Broad green-felt range. Lighting and table colors vary, so this is a first-pass estimate.
            Core.inRange(hsv, Scalar(30.0, 35.0, 35.0), Scalar(100.0, 255.0, 255.0), mask)
            val contours = ArrayList<MatOfPoint>()
            Imgproc.findContours(mask, contours, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            var tableRect: RectF? = null
            var largestArea = 0.0
            for (contour in contours) {
                val area = Imgproc.contourArea(contour)
                if (area > largestArea) {
                    largestArea = area
                    val r = Imgproc.boundingRect(contour)
                    tableRect = RectF(r.x.toFloat(), r.y.toFloat(),
                        (r.x + r.width).toFloat(), (r.y + r.height).toFloat())
                }
                contour.release()
            }

            Imgproc.HoughCircles(
                gray, circles, Imgproc.HOUGH_GRADIENT, 1.2,
                22.0, 100.0, 25.0, 7, 48
            )
            val detected = ArrayList<Triple<Float, Float, Float>>()
            for (i in 0 until circles.cols()) {
                val c = circles.get(0, i) ?: continue
                if (c.size >= 3) detected.add(Triple(c[0].toFloat(), c[1].toFloat(), c[2].toFloat()))
            }
            preview.setAnalysis(bitmap, tableRect, detected)
            status.text = buildString {
                append("Detected circles: ${detected.size}")
                append(if (tableRect != null) "  •  Possible table area found" else "  •  Table area not clear")
                append("\nFirst-pass computer vision. Review markers manually; results depend on image quality and lighting.")
            }
        } finally {
            rgba.release(); rgb.release(); hsv.release(); gray.release(); mask.release(); circles.release()
        }
    }
}

private class AnalysisPreview(context: android.content.Context) : View(context) {
    private var bitmap: Bitmap? = null
    private var table: RectF? = null
    private var balls: List<Triple<Float, Float, Float>> = emptyList()
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 235, 150)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val tablePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    fun setAnalysis(image: Bitmap, tableRect: RectF?, circles: List<Triple<Float, Float, Float>>) {
        bitmap = image
        table = tableRect
        balls = circles
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val image = bitmap ?: return
        val scale = min(width.toFloat() / image.width, height.toFloat() / image.height)
        val left = (width - image.width * scale) / 2f
        val top = (height - image.height * scale) / 2f
        val dst = RectF(left, top, left + image.width * scale, top + image.height * scale)
        canvas.drawBitmap(image, null, dst, imagePaint)
        table?.let {
            canvas.drawRect(
                left + it.left * scale, top + it.top * scale,
                left + it.right * scale, top + it.bottom * scale, tablePaint
            )
        }
        for ((x, y, radius) in balls) {
            canvas.drawCircle(left + x * scale, top + y * scale, radius * scale, markerPaint)
        }
    }
}
