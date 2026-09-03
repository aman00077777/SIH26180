package com.farmassist.app.ml

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class DiseaseInfo(
    val crop: String,
    val disease: String,
    val causeType: String,
    val description: String,
    val symptoms: List<String>,
    val immediateActions: List<String>,
    val prevention: List<String>,
    val expertHelp: String,
    val chemicalAvailable: Boolean,
    val chemicalLines: List<String>,
    val chemicalImportantNote: String,
    val farmerWarning: String,
    val dataQualityStatus: String,
    val cropCompatibilityWarning: String? = null,   // step 3: unusual-host / cross-crop warning
    val sourceCitations: List<String> = emptyList()  // resolved via source_registry.json
) {
    fun doseFor(acres: Double): String {
        return if (chemicalAvailable && chemicalLines.isNotEmpty()) {
            chemicalLines.joinToString("; ")
        } else {
            DiseaseInfoRepository.CHEMICAL_FALLBACK_MESSAGE
        }
    }
}

/**
 * Full rule-engine implementation of recommendation_rules.json's 8-step pipeline,
 * pulling from all knowledge-base assets: disease_database.json, pest_database.json,
 * healthy_classes_supplement.json, crop_pest_mapping.json, disease_crop_mapping.json,
 * source_registry.json.
 * (label_mapping_template.json is intentionally not used — all 102 rows are
 * NEEDS_VERIFICATION; matching is done by direct model_label string instead.)
 */
class DiseaseInfoRepository(context: Context) {

    private val diseaseByLabel: Map<String, JSONObject>
    private val pestByLabel: Map<String, JSONObject>
    private val healthyByLabel: Map<String, JSONObject>
    private val cropPestRecords: List<JSONObject>
    private val diseaseCropRecords: List<JSONObject>
    private val sourcesById: Map<String, JSONObject>
    val confidenceThreshold: Float
    val lowConfidenceMessage: String

    init {
        diseaseByLabel = safeLoadRecords(context, "disease_database.json") { it.optString("model_label") }
        pestByLabel = safeLoadRecords(context, "pest_database.json") { it.optString("model_label").lowercase() }
        healthyByLabel = safeLoadFlatArray(context, "healthy_classes_supplement.json") { it.optString("model_label") }
        cropPestRecords = safeLoadArray(context, "crop_pest_mapping.json", "records")
        diseaseCropRecords = safeLoadArray(context, "disease_crop_mapping.json", "records")
        sourcesById = safeLoadSourceRegistry(context)

        val rules = safeLoadObject(context, "recommendation_rules.json")
        confidenceThreshold = rules?.optJSONObject("confidence_threshold_config")
            ?.optDouble("default_threshold", 0.70)?.toFloat() ?: 0.70f
        lowConfidenceMessage = rules?.optJSONObject("confidence_threshold_config")
            ?.optString("low_confidence_message") ?: DEFAULT_LOW_CONFIDENCE_MESSAGE
    }

    // ---------- asset loading helpers ----------

    private fun safeLoadObject(context: Context, fileName: String): JSONObject? = try {
        JSONObject(context.assets.open(fileName).bufferedReader().use { it.readText() })
    } catch (e: Exception) { null }

    private fun safeLoadRecords(context: Context, fileName: String, keyOf: (JSONObject) -> String): Map<String, JSONObject> {
        return try {
            val root = safeLoadObject(context, fileName) ?: return emptyMap()
            val arr = root.getJSONArray("records")
            val map = mutableMapOf<String, JSONObject>()
            for (i in 0 until arr.length()) {
                val rec = arr.getJSONObject(i)
                val key = keyOf(rec)
                if (key.isNotBlank()) map[key] = rec
            }
            map
        } catch (e: Exception) { emptyMap() }
    }

    /** Loads a top-level JSON ARRAY (not wrapped in {"records":[...]}), e.g. healthy_classes_supplement.json. */
    private fun safeLoadFlatArray(context: Context, fileName: String, keyOf: (JSONObject) -> String): Map<String, JSONObject> {
        return try {
            val text = context.assets.open(fileName).bufferedReader().use { it.readText() }
            val arr = JSONArray(text)
            val map = mutableMapOf<String, JSONObject>()
            for (i in 0 until arr.length()) {
                val rec = arr.getJSONObject(i)
                val key = keyOf(rec)
                if (key.isNotBlank()) map[key] = rec
            }
            map
        } catch (e: Exception) { emptyMap() }
    }

    private fun safeLoadArray(context: Context, fileName: String, arrayKey: String): List<JSONObject> {
        return try {
            val root = safeLoadObject(context, fileName) ?: return emptyList()
            val arr = root.getJSONArray(arrayKey)
            (0 until arr.length()).map { arr.getJSONObject(it) }
        } catch (e: Exception) { emptyList() }
    }

    private fun safeLoadSourceRegistry(context: Context): Map<String, JSONObject> {
        return try {
            val root = safeLoadObject(context, "source_registry.json") ?: return emptyMap()
            val arr = root.getJSONArray("sources")
            val map = mutableMapOf<String, JSONObject>()
            for (i in 0 until arr.length()) {
                val src = arr.getJSONObject(i)
                val id = src.optString("source_id")
                if (id.isNotBlank()) map[id] = src
            }
            map
        } catch (e: Exception) { emptyMap() }
    }

    private fun jsonArrToList(obj: JSONObject?, key: String): List<String> {
        val arr = obj?.optJSONArray(key) ?: return emptyList()
        return (0 until arr.length()).map { arr.getString(it) }
    }

    private fun resolveSourceCitation(sourceId: String?): String? {
        if (sourceId.isNullOrBlank()) return null
        val src = sourcesById[sourceId] ?: return null
        val org = src.optString("organization", src.optString("source_name", ""))
        val title = src.optString("source_title", src.optString("source_name", ""))
        return if (org.isNotBlank() && title.isNotBlank() && org != title) "$org - $title" else org.ifBlank { title }
    }

    // ---------- Step 1: confidence gate ----------

    /** Returns lowConfidenceMessage if confidence is below threshold, else null (pass). */
    fun confidenceGate(confidence: Float): String? =
        if (confidence < confidenceThreshold) lowConfidenceMessage else null

    // ---------- Step 2-8: main lookup pipeline ----------

    /**
     * @param modelLabel exact label from the model's class_names / pest_class_names
     * @param selectedCrop the crop the farmer picked before scanning (nullable)
     * @param confidence prediction confidence, 0.0-1.0
     * @return null if confidence too low OR record not found (caller shows appropriate fallback)
     */
    fun infoFor(modelLabel: String, selectedCrop: String? = null, confidence: Float = 1f): DiseaseInfo? {
        if (confidence < confidenceThreshold) return null // step 1

        diseaseByLabel[modelLabel]?.let { return buildFromDisease(it, selectedCrop) }
        pestByLabel[modelLabel.lowercase()]?.let { return buildFromPest(it, selectedCrop) }
        healthyByLabel[modelLabel]?.let { return buildFromHealthy(it) }
        return null // step 2: not in knowledge base
    }

    private fun buildFromDisease(rec: JSONObject, selectedCrop: String?): DiseaseInfo {
        val crop = rec.optJSONObject("crop")
        val condition = rec.optJSONObject("condition")
        val identification = rec.optJSONObject("identification")
        val management = rec.optJSONObject("management")
        val chemGuidance = management?.optJSONObject("official_chemical_guidance")
        val chemAvailable = chemGuidance?.optBoolean("available", false) ?: false

        val chemLines = mutableListOf<String>()
        val chemSourceIds = mutableListOf<String>()
        if (chemAvailable) {
            chemGuidance?.optJSONArray("guidance")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val g = arr.getJSONObject(i)
                    chemLines.add("${g.optString("active_ingredient")} - ${g.optString("dose")}")
                    g.optString("source_id").takeIf { it.isNotBlank() }?.let { chemSourceIds.add(it) }
                }
            }
        }

        // Tiers A-D per recommendation_rules.json step 5
        val combinedActions = mutableListOf<String>()
        combinedActions += jsonArrToList(management, "immediate_non_chemical_actions")
        combinedActions += jsonArrToList(management, "cultural_management")
        combinedActions += jsonArrToList(management, "mechanical_or_physical_management")
        combinedActions += jsonArrToList(management, "biological_management")

        val dataQuality = rec.optJSONObject("data_quality")?.optString("status") ?: "unknown"

        // Step 3: crop compatibility check. Uses model_label's crop prefix (not crop.common_name)
        // because model_label shares the exact same crop-key vocabulary as crop_to_classes_mapping.json
        // (both derived from PlantVillage folder names), whereas crop.common_name is a free-text
        // display string that doesn't always match (e.g. "Corn (Maize)" vs "Corn_(maize)").
        val cropCompatWarning = checkDiseaseCropCompatibility(rec, rec.optString("model_label"), selectedCrop)

        // Sources: record's own sources[] plus any chemical guidance source_ids resolved via registry
        val sourceCitations = mutableListOf<String>()
        rec.optJSONArray("sources")?.let { arr ->
            for (i in 0 until arr.length()) {
                val s = arr.getJSONObject(i)
                val name = s.optString("source_name")
                val title = s.optString("source_title")
                if (name.isNotBlank()) sourceCitations.add(if (title.isNotBlank()) "$name - $title" else name)
            }
        }
        chemSourceIds.distinct().forEach { id -> resolveSourceCitation(id)?.let { sourceCitations.add(it) } }

        return DiseaseInfo(
            crop = crop?.optString("common_name") ?: "-",
            disease = condition?.optString("common_name") ?: "-",
            causeType = condition?.optString("condition_type") ?: "-",
            description = identification?.optString("description") ?: "-",
            symptoms = jsonArrToList(identification, "symptoms"),
            immediateActions = combinedActions,
            prevention = jsonArrToList(management, "prevention"),
            expertHelp = jsonArrToList(rec, "expert_consultation_required_when").joinToString("; "),
            chemicalAvailable = chemAvailable,
            chemicalLines = chemLines,
            chemicalImportantNote = chemGuidance?.optString("important_note") ?: "",
            farmerWarning = rec.optString("farmer_warning", ""),
            dataQualityStatus = dataQuality,
            cropCompatibilityWarning = cropCompatWarning,
            sourceCitations = sourceCitations.distinct()
        )
    }

    private fun buildFromPest(rec: JSONObject, selectedCrop: String?): DiseaseInfo {
        val crop = rec.optJSONObject("crop")
        val pest = rec.optJSONObject("pest")
        val identification = rec.optJSONObject("identification")
        val ipm = rec.optJSONObject("integrated_pest_management")
        val chemGuidance = ipm?.optJSONObject("official_chemical_guidance")
        val chemAvailable = chemGuidance?.optBoolean("available", false) ?: false

        val chemLines = mutableListOf<String>()
        if (chemAvailable) {
            chemGuidance?.optJSONArray("guidance")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val g = arr.getJSONObject(i)
                    chemLines.add("${g.optString("active_ingredient")} - ${g.optString("dose")}")
                }
            }
        }

        val combinedActions = mutableListOf<String>()
        combinedActions += jsonArrToList(ipm, "cultural")
        combinedActions += jsonArrToList(ipm, "mechanical_physical")
        combinedActions += jsonArrToList(ipm, "biological")
        combinedActions += jsonArrToList(rec, "monitoring")

        val dataQuality = rec.optJSONObject("data_quality")?.optString("status") ?: "unknown"

        // Step 3: crop compatibility check using crop_pest_mapping.json verified_host records
        val pestId = rec.optString("id")
        val cropCompatWarning = checkPestCropCompatibility(pestId, selectedCrop)

        val sourceCitations = mutableListOf<String>()
        rec.optJSONArray("sources")?.let { arr ->
            for (i in 0 until arr.length()) {
                val s = arr.getJSONObject(i)
                val name = s.optString("source_name")
                val title = s.optString("source_title")
                if (name.isNotBlank()) sourceCitations.add(if (title.isNotBlank()) "$name - $title" else name)
            }
        }

        return DiseaseInfo(
            crop = crop?.optString("common_name") ?: "-",
            disease = pest?.optString("common_name") ?: "-",
            causeType = pest?.optString("condition_type") ?: "insect pest",
            description = identification?.optString("description") ?: "-",
            symptoms = jsonArrToList(rec, "damage_symptoms"),
            immediateActions = combinedActions,
            prevention = emptyList(),
            expertHelp = jsonArrToList(rec, "expert_consultation_required_when").joinToString("; "),
            chemicalAvailable = chemAvailable,
            chemicalLines = chemLines,
            chemicalImportantNote = chemGuidance?.optString("important_note") ?: "",
            farmerWarning = "",
            dataQualityStatus = dataQuality,
            cropCompatibilityWarning = cropCompatWarning,
            sourceCitations = sourceCitations.distinct()
        )
    }

    /** Builds a DiseaseInfo for a "___healthy" model_label from healthy_classes_supplement.json.
     *  This file's schema is FLAT (no nested crop/condition/management objects), unlike
     *  disease_database.json, so its fields are read directly off the record. */
    private fun buildFromHealthy(rec: JSONObject): DiseaseInfo = DiseaseInfo(
        crop = rec.optString("crop", "-"),
        disease = rec.optString("disease", "Healthy"),
        causeType = rec.optString("cause_type", "N/A"),
        description = rec.optString("description", "-"),
        symptoms = jsonArrToList(rec, "symptoms"),
        immediateActions = jsonArrToList(rec, "immediate_actions"),
        prevention = jsonArrToList(rec, "prevention"),
        expertHelp = rec.optString("expert_help", ""),
        chemicalAvailable = false,
        chemicalLines = emptyList(),
        chemicalImportantNote = "",
        farmerWarning = "",
        dataQualityStatus = "verified",
        cropCompatibilityWarning = null,
        sourceCitations = emptyList()
    )

    // ---------- Step 3 helpers: crop compatibility ----------

    private fun checkPestCropCompatibility(pestId: String, selectedCrop: String?): String? {
        if (selectedCrop.isNullOrBlank() || pestId.isBlank()) return null
        val match = cropPestRecords.firstOrNull {
            it.optString("pest_id") == pestId && it.optString("crop").equals(selectedCrop, ignoreCase = true)
        }
        return when {
            match == null -> "This pest is unusual for $selectedCrop — consider re-verifying with a clearer photo or a local expert."
            !(match.optJSONObject("relationship")?.optBoolean("verified_host", false) ?: false) ->
                "$selectedCrop is not a confirmed host for this pest — treat this result with caution."
            else -> null
        }
    }

    /**
     * @param modelLabel used (via its "___" prefix) to derive the record's crop key, since that key
     * shares the same vocabulary as crop_to_classes_mapping.json / selectedCrop — unlike the record's
     * free-text crop.common_name display string, which can differ (e.g. "Orange" vs "Orange / Citrus").
     */
    private fun checkDiseaseCropCompatibility(diseaseRec: JSONObject, modelLabel: String, selectedCrop: String?): String? {
        if (selectedCrop.isNullOrBlank()) return null
        val recordCropKey = modelLabel.substringBefore("___")
        if (!recordCropKey.equals(selectedCrop, ignoreCase = true)) {
            val displayCrop = diseaseRec.optJSONObject("crop")?.optString("common_name") ?: recordCropKey
            return "This result is documented for $displayCrop, not $selectedCrop — treat this prediction as unusual and consider expert re-verification."
        }
        // Even on a crop match, check disease_crop_mapping.json for a genuine cross-crop pathogen note
        val diseaseId = diseaseRec.optString("id")
        val mapping = diseaseCropRecords.firstOrNull { it.optString("disease_id") == diseaseId }
        val note = mapping?.optString("cross_crop_note")
        return if (!note.isNullOrBlank() && note.contains("NOT VERIFIED", ignoreCase = true)) note else null
    }

    companion object {
        const val NOT_IN_KNOWLEDGE_BASE_MESSAGE =
            "This condition is not yet in our verified knowledge base. Please consult your local KVK or agriculture department."
        const val CHEMICAL_FALLBACK_MESSAGE =
            "Consult your local KVK, agriculture department, or licensed agricultural expert for currently approved treatment options."
        const val AI_DISCLAIMER =
            "AI predictions are decision-support information and should not replace professional agricultural advice. Treatment decisions should follow current, crop-specific, and region-specific guidance."
        const val DEFAULT_LOW_CONFIDENCE_MESSAGE =
            "Prediction confidence is low. Please upload a clearer image or consult an agricultural expert."
    }
}
