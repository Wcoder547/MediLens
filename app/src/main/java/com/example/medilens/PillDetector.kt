package com.example.medilens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class PillDetector(private val context: Context) {

    private var interpreter: Interpreter? = null
    private val TAG = "PillDetector"

    // Must match your training: 4 classes in this exact order
    private val classNames = listOf("myteka", "panadol", "risek", "ventolin")

    // Input size your model was trained on
    private val INPUT_SIZE = 640
    private val CONFIDENCE_THRESHOLD = 0.60f
    private val IOU_THRESHOLD = 0.45f

    // Output tensor shape: [1, 8, 8400]
    // Row 0-3 = cx, cy, w, h  |  Row 4-7 = class scores
    private val NUM_CLASSES = 4
    private val NUM_ANCHORS = 8400
    private val OUTPUT_ROWS = 4 + NUM_CLASSES  // = 8

    init {
        loadModel()
    }

    private fun loadModel() {
        try {
            val assetFileDescriptor = context.assets.openFd("pill_detector.tflite")
            val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = assetFileDescriptor.startOffset
            val declaredLength = assetFileDescriptor.declaredLength
            val modelBuffer = fileChannel.map(
                FileChannel.MapMode.READ_ONLY, startOffset, declaredLength
            )
            val options = Interpreter.Options().apply {
                numThreads = 4
            }
            interpreter = Interpreter(modelBuffer, options)
            Log.d(TAG, "✅ TFLite model loaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to load TFLite model: ${e.message}", e)
            throw RuntimeException("Could not load pill_detector.tflite from assets: ${e.message}")
        }
    }

    /**
     * Main entry point. Accepts a Bitmap (already EXIF-corrected),
     * returns the same List<DetectedPill> that Roboflow used to return.
     */
    fun detect(bitmap: Bitmap): List<VerificationLoadingActivity.DetectedPill> {
        val interpreter = this.interpreter ?: return emptyList()

        // 1. Scale bitmap to INPUT_SIZE × INPUT_SIZE, remember scale factors for coord mapping
        val (scaledBitmap, scaleX, scaleY, padLeft, padTop) = letterboxBitmap(bitmap)

        // 2. Convert bitmap → ByteBuffer [1, 640, 640, 3] float32 normalized [0,1]
        val inputBuffer = bitmapToByteBuffer(scaledBitmap)

        // 3. Allocate output buffer: [1, 8, 8400]
        val outputArray = Array(1) { Array(OUTPUT_ROWS) { FloatArray(NUM_ANCHORS) } }

        // 4. Run inference
        try {
            interpreter.run(inputBuffer, outputArray)
        } catch (e: Exception) {
            Log.e(TAG, "Inference failed: ${e.message}", e)
            return emptyList()
        }

        // 5. Parse raw output → candidate detections
        val rawDetections = parseOutput(outputArray[0])

        // 6. NMS → final detections
        val finalDetections = nonMaxSuppression(rawDetections)

        // 7. Map coordinates back to EXIF-corrected bitmap pixel space
        // YOLOv11 TFLite exports coords in normalized [0,1] space relative to input size.
        // First multiply by INPUT_SIZE to get pixel coords in 640×640 space,
        // then remove letterbox padding, then scale to original bitmap dims.
        val contentW = INPUT_SIZE - padLeft * 2f
        val contentH = INPUT_SIZE - padTop  * 2f

        return finalDetections.map { det ->
            // Step A: convert normalized [0,1] → pixel coords in 640×640 space
            val cxPx = det.cx * INPUT_SIZE
            val cyPx = det.cy * INPUT_SIZE
            val wPx  = det.w  * INPUT_SIZE
            val hPx  = det.h  * INPUT_SIZE

            // Step B: remove letterbox padding offset, scale to original bitmap dims
            val origCx = ((cxPx - padLeft) / contentW) * bitmap.width
            val origCy = ((cyPx - padTop)  / contentH) * bitmap.height
            val origW  =  (wPx  / contentW) * bitmap.width
            val origH  =  (hPx  / contentH) * bitmap.height

            VerificationLoadingActivity.DetectedPill(
                className  = classNames.getOrElse(det.classIdx) { "unknown" },
                confidence = det.confidence,
                x          = origCx.coerceIn(0f, bitmap.width.toFloat()),
                y          = origCy.coerceIn(0f, bitmap.height.toFloat()),
                width      = origW,
                height     = origH
            )
        }
    }

    // ── Letterbox resize: keeps aspect ratio, pads to square ─────────────
    private data class LetterboxResult(
        val bitmap: Bitmap,
        val scaleX: Float,
        val scaleY: Float,
        val padLeft: Float,
        val padTop: Float
    )

    private fun letterboxBitmap(src: Bitmap): LetterboxResult {
        val srcW = src.width.toFloat()
        val srcH = src.height.toFloat()
        val scale = minOf(INPUT_SIZE / srcW, INPUT_SIZE / srcH)
        val newW = (srcW * scale).toInt()
        val newH = (srcH * scale).toInt()

        val padLeft = (INPUT_SIZE - newW) / 2f
        val padTop  = (INPUT_SIZE - newH) / 2f

        val result = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(result)

        // Gray background (matches YOLO letterbox default)
        canvas.drawColor(android.graphics.Color.rgb(114, 114, 114))

        val scaled = Bitmap.createScaledBitmap(src, newW, newH, true)
        canvas.drawBitmap(scaled, padLeft, padTop, null)

        return LetterboxResult(result, scale, scale, padLeft, padTop)
    }

    // ── Bitmap → normalized ByteBuffer ───────────────────────────────────
    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        // [1, HEIGHT, WIDTH, 3] NHWC float32
        val byteBuffer = ByteBuffer.allocateDirect(
            1 * INPUT_SIZE * INPUT_SIZE * 3 * 4  // 4 bytes per float
        )
        byteBuffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        for (pixel in pixels) {
            val r = ((pixel shr 16) and 0xFF) / 255.0f
            val g = ((pixel shr 8)  and 0xFF) / 255.0f
            val b = (pixel          and 0xFF) / 255.0f
            byteBuffer.putFloat(r)
            byteBuffer.putFloat(g)
            byteBuffer.putFloat(b)
        }
        byteBuffer.rewind()
        return byteBuffer
    }

    // ── Parse output tensor [8, 8400] ─────────────────────────────────────
    private data class RawDetection(
        val cx: Float, val cy: Float,
        val w: Float,  val h: Float,
        val confidence: Float,
        val classIdx: Int
    )

    private fun parseOutput(output: Array<FloatArray>): List<RawDetection> {
        val detections = mutableListOf<RawDetection>()

        for (a in 0 until NUM_ANCHORS) {
            val cx = output[0][a]
            val cy = output[1][a]
            val w  = output[2][a]
            val h  = output[3][a]

            // Find best class score
            var bestScore = 0f
            var bestClass = 0
            for (c in 0 until NUM_CLASSES) {
                val score = output[4 + c][a]
                if (score > bestScore) {
                    bestScore = score
                    bestClass = c
                }
            }

            if (bestScore >= CONFIDENCE_THRESHOLD) {
                detections.add(RawDetection(cx, cy, w, h, bestScore, bestClass))
            }
        }
        return detections
    }

    // ── Non-Maximum Suppression ───────────────────────────────────────────
    private fun nonMaxSuppression(detections: List<RawDetection>): List<RawDetection> {
        if (detections.isEmpty()) return emptyList()

        // Group by class, then apply NMS within each class
        val result = mutableListOf<RawDetection>()
        val byClass = detections.groupBy { it.classIdx }

        for ((_, classDets) in byClass) {
            val sorted = classDets.sortedByDescending { it.confidence }.toMutableList()
            val kept = mutableListOf<RawDetection>()

            while (sorted.isNotEmpty()) {
                val best = sorted.removeAt(0)
                kept.add(best)
                sorted.removeAll { iou(best, it) > IOU_THRESHOLD }
            }
            result.addAll(kept)
        }
        return result
    }

    private fun iou(a: RawDetection, b: RawDetection): Float {
        val aLeft   = a.cx - a.w / 2f;  val aRight  = a.cx + a.w / 2f
        val aTop    = a.cy - a.h / 2f;  val aBottom = a.cy + a.h / 2f
        val bLeft   = b.cx - b.w / 2f;  val bRight  = b.cx + b.w / 2f
        val bTop    = b.cy - b.h / 2f;  val bBottom = b.cy + b.h / 2f

        val interLeft   = maxOf(aLeft, bLeft)
        val interTop    = maxOf(aTop, bTop)
        val interRight  = minOf(aRight, bRight)
        val interBottom = minOf(aBottom, bBottom)

        val interArea = maxOf(0f, interRight - interLeft) * maxOf(0f, interBottom - interTop)
        val aArea     = a.w * a.h
        val bArea     = b.w * b.h
        val unionArea = aArea + bArea - interArea

        return if (unionArea <= 0f) 0f else interArea / unionArea
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}