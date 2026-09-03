package com.farmassist.app.ml

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Loads crop_to_classes_mapping.json and exposes, for a chosen crop, the set of
 * disease-model class indices that belong to it. Used to restrict the classifier's
 * argmax search to only classes relevant to the crop the farmer selected — so the
 * model isn't guessing across crops it wasn't shown a photo of.
 */
class CropClassMapping(context: Context, fileName: String = "crop_to_classes_mapping.json") {

    // cropName -> list of (classIndex, className)
    private val mapping: Map<String, List<Pair<Int, String>>>

    init {
        mapping = try {
            val json = context.assets.open(fileName).bufferedReader().use { it.readText() }
            val root = JSONObject(json)
            val result = mutableMapOf<String, List<Pair<Int, String>>>()
            root.keys().forEach { crop ->
                val arr: JSONArray = root.getJSONArray(crop)
                val list = (0 until arr.length()).map { i ->
                    val obj = arr.getJSONObject(i)
                    obj.getInt("index") to obj.getString("class_name")
                }
                result[crop] = list
            }
            result
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /** Crop names in a friendly display order (as they appear in the JSON). */
    fun cropNames(): List<String> = mapping.keys.toList()

    /** The set of disease-model class indices valid for this crop. Empty if crop unknown. */
    fun indicesFor(crop: String): Set<Int> = mapping[crop]?.map { it.first }?.toSet() ?: emptySet()

    /** Human-friendly display name — collapses underscores/parens for the crop picker UI. */
    fun displayName(crop: String): String =
        crop.replace("_(including_sour)", "").replace("_(maize)", "").replace(",_bell", " (Bell)").replace("_", " ")
}
