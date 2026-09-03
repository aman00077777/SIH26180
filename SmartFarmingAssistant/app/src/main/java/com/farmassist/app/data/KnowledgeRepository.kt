package com.farmassist.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class DiseaseResult(
    val id: String,
    val commonName: String,
    val cropName: String,
    val description: String,
    val symptoms: List<String>,
    val immediateActions: List<String>,
    val culturalManagement: List<String>,
    val mechanicalManagement: List<String>,
    val biologicalManagement: List<String>,
    val chemicalAvailable: Boolean,
    val chemicalGuidance: List<Triple<String, String, String>>, // active_ingredient, dose, source_id
    val chemicalImportantNote: String,
    val prevention: List<String>,
    val severityGuidance: Map<String, List<String>>,
    val farmerWarning: String,
    val dataQualityStatus: String,
    val sourceUrls: List<String>
)

class KnowledgeRepository(private val context: Context) {

    private fun readAsset(fileName: String): String =
        context.assets.open(fileName).bufferedReader().use { it.readText() }

    private val classNames: List<String> by lazy {
        val arr = JSONArray(readAsset("class_names.json"))
        (0 until arr.length()).map { arr.getString(it) }
    }

    private val pestClassNames: List<String> by lazy {
        val arr = JSONArray(readAsset("pest_class_names.json"))
        (0 until arr.length()).map { arr.getString(it) }
    }

    private val diseaseDb: JSONObject by lazy { JSONObject(readAsset("disease_database.json")) }
    private val pestDb: JSONObject by lazy { JSONObject(readAsset("pest_database.json")) }

    /** Step: model output index -> model_label string (disease model) */
    fun diseaseLabelForIndex(index: Int): String? = classNames.getOrNull(index)

    /** Step: model output index -> model_label string (pest model) */
    fun pestLabelForIndex(index: Int): String? = pestClassNames.getOrNull(index)

    /** Look up a disease record by its model_label (exact match against disease_database.json) */
    fun findDiseaseByModelLabel(modelLabel: String): JSONObject? {
        val records = diseaseDb.getJSONArray("records")
        for (i in 0 until records.length()) {
            val rec = records.getJSONObject(i)
            if (rec.optString("model_label") == modelLabel) return rec
        }
        return null
    }

    /** Look up a pest record by its model_label field (case-insensitive, since pest_class_names.json
     *  uses lowercase strings like "brown plant hopper") */
    fun findPestByModelLabel(modelLabel: String): JSONObject? {
        val records = pestDb.getJSONArray("records")
        for (i in 0 until records.length()) {
            val rec = records.getJSONObject(i)
            val recLabel = rec.optString("model_label")
            if (recLabel.equals(modelLabel, ignoreCase = true)) return rec
        }
        return null
    }

    /**
     * Full pipeline per recommendation_rules.json:
     * step 1: confidence gate
     * step 2: identify record (returns null -> caller shows "not in knowledge base" message)
     * step 4: data_quality gate
     * step 6: chemical guidance gate (never shows chem info unless available == true)
     */
    fun buildDiseaseResult(modelLabel: String, confidence: Float, threshold: Float = 0.70f): DiseaseResult? {
        if (confidence < threshold) return null // caller shows low_confidence_message

        val rec = findDiseaseByModelLabel(modelLabel) ?: return null // caller shows "not in KB" message

        val crop = rec.getJSONObject("crop")
        val condition = rec.getJSONObject("condition")
        val identification = rec.getJSONObject("identification")
        val management = rec.getJSONObject("management")
        val chemGuidance = management.getJSONObject("official_chemical_guidance")
        val chemAvailable = chemGuidance.optBoolean("available", false)

        val chemList = mutableListOf<Triple<String, String, String>>()
        if (chemAvailable) {
            val arr = chemGuidance.getJSONArray("guidance")
            for (i in 0 until arr.length()) {
                val g = arr.getJSONObject(i)
                chemList.add(
                    Triple(
                        g.optString("active_ingredient"),
                        g.optString("dose"),
                        g.optString("source_id")
                    )
                )
            }
        }

        val severityMap = mutableMapOf<String, List<String>>()
        val sevObj = rec.optJSONObject("severity_guidance")
        sevObj?.keys()?.forEach { key ->
            val arr = sevObj.getJSONArray(key)
            severityMap[key] = (0 until arr.length()).map { arr.getString(it) }
        }

        val sourceUrls = mutableListOf<String>()
        val sourcesArr = rec.optJSONArray("sources")
        sourcesArr?.let {
            for (i in 0 until it.length()) {
                sourceUrls.add(it.getJSONObject(i).optString("source_url"))
            }
        }

        fun jsonArrToList(obj: JSONObject, key: String): List<String> {
            val arr = obj.optJSONArray(key) ?: return emptyList()
            return (0 until arr.length()).map { arr.getString(it) }
        }

        return DiseaseResult(
            id = rec.optString("id"),
            commonName = condition.optString("common_name"),
            cropName = crop.optString("common_name"),
            description = identification.optString("description"),
            symptoms = jsonArrToList(identification, "symptoms"),
            immediateActions = jsonArrToList(management, "immediate_non_chemical_actions"),
            culturalManagement = jsonArrToList(management, "cultural_management"),
            mechanicalManagement = jsonArrToList(management, "mechanical_or_physical_management"),
            biologicalManagement = jsonArrToList(management, "biological_management"),
            chemicalAvailable = chemAvailable,
            chemicalGuidance = chemList,
            chemicalImportantNote = chemGuidance.optString("important_note"),
            prevention = jsonArrToList(management, "prevention"),
            severityGuidance = severityMap,
            farmerWarning = rec.optString("farmer_warning"),
            dataQualityStatus = rec.optJSONObject("data_quality")?.optString("status") ?: "unknown",
            sourceUrls = sourceUrls
        )
    }

    companion object {
        const val LOW_CONFIDENCE_MESSAGE =
            "Prediction confidence is low. Please upload a clearer image or consult an agricultural expert."
        const val NOT_IN_KNOWLEDGE_BASE_MESSAGE =
            "This condition is not yet in our verified knowledge base. Please consult your local KVK or agriculture department."
        const val CHEMICAL_FALLBACK_MESSAGE =
            "Consult your local KVK, agriculture department, or licensed agricultural expert for currently approved treatment options."
        const val AI_DISCLAIMER =
            "AI predictions are decision-support information and should not replace professional agricultural advice. Treatment decisions should follow current, crop-specific, and region-specific guidance."
    }
}