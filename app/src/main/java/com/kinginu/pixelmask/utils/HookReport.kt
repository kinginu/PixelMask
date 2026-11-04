package com.kinginu.pixelmask.utils

import android.content.Context
import com.kinginu.pixelmask.Constants
import org.json.JSONObject

/**
 * What the hook inside Google Photos actually did, sent back to the Manager so the
 * Home screen and issue reports can tell "hook never ran" from "hook ran but failed"
 * from "hook worked but Photos still says no".
 *
 * Travels as a JSON string in a broadcast extra, so both sides only need org.json.
 */
data class HookReport(
    val timestamp: Long,
    val processName: String,
    val moduleVersionCode: Int,
    val framework: String,
    val photosVersionName: String,
    val photosVersionCode: Long,
    val masterEnabled: Boolean,
    // False also when the settings file doesn't exist yet (never saved), in which
    // case the hook just uses defaults. Informational only, not an error.
    val prefsReadable: Boolean,
    val target: String,
    val buildSpoof: String,
    val seenModel: String,
    val seenFingerprint: String,
    val featureHook: String,
    val featureTrue: Int,
    val featureFalse: Int,
) {
    /** True when every step the hook attempted succeeded. */
    val isHealthy: Boolean
        get() = masterEnabled && buildSpoof == OK && featureHook == OK

    /** First thing that went wrong, for a one-line summary. */
    val firstProblem: String?
        get() = when {
            !masterEnabled -> null
            buildSpoof != OK -> "Build: $buildSpoof"
            featureHook != OK -> "Features: $featureHook"
            else -> null
        }

    fun toJson(): String = JSONObject().apply {
        put("timestamp", timestamp)
        put("processName", processName)
        put("moduleVersionCode", moduleVersionCode)
        put("framework", framework)
        put("photosVersionName", photosVersionName)
        put("photosVersionCode", photosVersionCode)
        put("masterEnabled", masterEnabled)
        put("prefsReadable", prefsReadable)
        put("target", target)
        put("buildSpoof", buildSpoof)
        put("seenModel", seenModel)
        put("seenFingerprint", seenFingerprint)
        put("featureHook", featureHook)
        put("featureTrue", featureTrue)
        put("featureFalse", featureFalse)
    }.toString()

    /** Multi-line block for the issue report's auto-collected context. */
    fun toDiagLines(now: Long = System.currentTimeMillis()): String = buildString {
        appendLine("Hook report age:     ${(now - timestamp) / 1000}s")
        appendLine("Hook process:        $processName")
        appendLine("Hook module code:    $moduleVersionCode")
        appendLine("Framework:           $framework")
        appendLine("Photos (in hook):    $photosVersionName (code $photosVersionCode)")
        appendLine("Hook master switch:  $masterEnabled")
        appendLine("Hook prefs readable: $prefsReadable")
        appendLine("Hook target:         $target")
        appendLine("Build spoof:         $buildSpoof")
        appendLine("Photos sees MODEL:   $seenModel")
        appendLine("Photos sees FP:      $seenFingerprint")
        appendLine("Feature hook:        $featureHook")
        append("Feature answers:     true=$featureTrue false=$featureFalse")
    }

    companion object {
        const val OK = "ok"
        const val SKIPPED = "skipped"

        fun fromJson(json: String?): HookReport? = runCatching {
            val o = JSONObject(json ?: return null)
            HookReport(
                timestamp = o.getLong("timestamp"),
                processName = o.optString("processName"),
                moduleVersionCode = o.optInt("moduleVersionCode"),
                framework = o.optString("framework"),
                photosVersionName = o.optString("photosVersionName"),
                photosVersionCode = o.optLong("photosVersionCode"),
                masterEnabled = o.optBoolean("masterEnabled", true),
                prefsReadable = o.optBoolean("prefsReadable", true),
                target = o.optString("target"),
                buildSpoof = o.optString("buildSpoof"),
                seenModel = o.optString("seenModel"),
                seenFingerprint = o.optString("seenFingerprint"),
                featureHook = o.optString("featureHook"),
                featureTrue = o.optInt("featureTrue"),
                featureFalse = o.optInt("featureFalse"),
            )
        }.getOrNull()

        /** Short "Class: message" form of a Throwable, safe to show in UI. */
        fun describe(t: Throwable): String {
            val root = generateSequence(t) { it.cause }.last()
            return "${root.javaClass.simpleName}: ${root.message ?: "(no message)"}".take(300)
        }

        fun load(context: Context): HookReport? = fromJson(
            context.getSharedPreferences(Constants.HOOK_STATUS_PREF_FILE, Context.MODE_PRIVATE)
                .getString(Constants.PREF_LAST_HOOK_REPORT, null)
        )

        fun save(context: Context, json: String) {
            context.getSharedPreferences(Constants.HOOK_STATUS_PREF_FILE, Context.MODE_PRIVATE)
                .edit().putString(Constants.PREF_LAST_HOOK_REPORT, json).apply()
        }
    }
}
