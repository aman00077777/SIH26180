package com.farmassist.app.ml

import android.content.Context
import android.graphics.Bitmap
import org.json.JSONArray
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.nnapi.NnApiDelegate
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.io.IOException

data class ClassificationResult(
    val label: String,
    val confidence: Float,   // 0.0–1.0
    val classIndex: Int
)

/**
 * Generic INT8 TFLite image classifier.
 *
 * Built to be reused for BOTH the disease model and the pest model — pass in the
 * .tflite filename and the matching class-names JSON filename (both must live in
 * app/src/main/assets/).
 *
 * IMPORTANT (per model spec): input is 224x224x3 uint8 — raw 0-255 pixel values,
 * NOT normalized to 0-1 float. Output is an array of uint8 scores; argmax = predicted class.
 * Getting this wrong produces no error, just garbage predictions — see handoff notes.
 */
class TFLiteClassifier(
    context: Context,
    modelFileName: String,
    labelsFileName: String,
    private val inputSize: Int = 224
) {
    private var interpreter: Interpreter
    private var nnApiDelegate: NnApiDelegate? = null
    val classNames: List<String>

    init {
        classNames = loadClassNames(context, labelsFileName)

        val model = FileUtil.loadMappedFile(context, modelFileName)
        val options = Interpreter.Options()

        // Try to route through the NNAPI delegate -> Snapdragon Hexagon NPU.
        // If the device/driver doesn't support it, fall back to CPU rather than crash.
        try {
            val delegate = NnApiDelegate()
            options.addDelegate(delegate)
            nnApiDelegate = delegate
        } catch (e: Exception) {
            // NNAPI unavailable on this device — CPU fallback is still fully functional offline.
            nnApiDelegate = null
        }
        options.setNumThreads(4)

        interpreter = try {
            Interpreter(model, options)
        } catch (e: Exception) {
            // Some devices' NNAPI drivers reject the delegate at run-creation time.
            // Rebuild without it so the app never hard-fails just because NPU routing failed.
            nnApiDelegate?.close()
            nnApiDelegate = null
            val cpuOptions = Interpreter.Options().apply { setNumThreads(4) }
            Interpreter(model, cpuOptions)
        }
    }

    /** True if this classifier is actually running on the NPU delegate right now. */
    fun isUsingNnApi(): Boolean = nnApiDelegate != null

    /**
     * @param allowedIndices when non-null, restricts the argmax search to only these
     *   class indices (e.g. only the disease classes belonging to the crop the farmer
     *   selected). When null, searches all classes as before.
     */
    fun predict(bitmap: Bitmap, allowedIndices: Set<Int>? = null): ClassificationResult {
        val tensorImage = TensorImage(org.tensorflow.lite.DataType.UINT8)
        tensorImage.load(bitmap)

        val processor = ImageProcessor.Builder()
            .add(ResizeOp(inputSize, inputSize, ResizeOp.ResizeMethod.BILINEAR))
            .build()
        val processed = processor.process(tensorImage)

        val output = Array(1) { ByteArray(classNames.size) }
        interpreter.run(processed.buffer, output)

        val scores = output[0]
        var maxIdx = -1
        var maxVal = -1
        val candidateIndices = allowedIndices ?: scores.indices.toSet()
        for (i in candidateIndices) {
            if (i !in scores.indices) continue
            val v = scores[i].toInt() and 0xFF   // uint8 unsigned conversion
            if (v > maxVal) {
                maxVal = v
                maxIdx = i
            }
        }
        if (maxIdx == -1) { maxIdx = 0; maxVal = scores[0].toInt() and 0xFF }
        val confidence = maxVal / 255f
        return ClassificationResult(classNames[maxIdx], confidence, maxIdx)
    }

    fun close() {
        interpreter.close()
        nnApiDelegate?.close()
    }

    companion object {
        @Throws(IOException::class)
        private fun loadClassNames(context: Context, fileName: String): List<String> {
            val json = context.assets.open(fileName).bufferedReader().use { it.readText() }
            val arr = JSONArray(json)
            return (0 until arr.length()).map { arr.getString(it) }
        }
    }
}
