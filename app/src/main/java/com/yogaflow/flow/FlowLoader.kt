package com.yogaflow.flow

import android.content.Context

object FlowLoader {

    private const val FLOW_ASSET_DIR = "flows"

    fun loadFromAssets(context: Context, fileName: String): YogaFlow {
        val text = context.assets.open(fileName).bufferedReader().use { it.readText() }
        return FlowParser.parse(text)
    }

    fun loadAllFromAssets(context: Context): List<YogaFlow> {
        val flowFiles = context.assets.list(FLOW_ASSET_DIR)
            ?.filter { it.endsWith(".flow.txt") }
            ?.sorted()
            .orEmpty()

        return flowFiles.map { fileName ->
            loadFromAssets(context, "$FLOW_ASSET_DIR/$fileName")
        }
    }
}
