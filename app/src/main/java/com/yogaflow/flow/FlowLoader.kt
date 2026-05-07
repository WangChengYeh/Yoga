package com.yogaflow.flow

import android.content.Context
import android.util.Log

object FlowLoader {

    private const val FLOW_ASSET_DIR = "flows"

    fun loadFromAssets(context: Context, fileName: String): YogaFlow {
        val text = context.assets.open(fileName).bufferedReader().use { it.readText() }
        return FlowParser.parse(text)
    }

    fun loadAllFromAssets(context: Context): List<YogaFlow> {
        val flowFiles = context.assets.list(FLOW_ASSET_DIR)
            ?.filter { it.endsWith(".flow.json") }
            ?.sorted()
            .orEmpty()

        return flowFiles.map { fileName ->
            val path = "$FLOW_ASSET_DIR/$fileName"
            runCatching {
                loadFromAssets(context, path)
            }.getOrElse { e ->
                Log.e("YogaFlow", "Skipping invalid flow asset '$path': ${e.message}")
                null
            }
        }.filterNotNull()
    }

    fun loadByPose(context: Context, vararg poseIds: String): List<YogaFlow> {
        val poses = poseIds.toSet()
        val flowFiles = context.assets.list(FLOW_ASSET_DIR)
            ?.filter { it.endsWith(".flow.json") }
            ?.sorted()
            .orEmpty()

        return flowFiles.mapNotNull { fileName ->
            val path = "$FLOW_ASSET_DIR/$fileName"
            runCatching { loadFromAssets(context, path) }
                .getOrNull()
        }.filter { it.pose in poses }
    }
}
