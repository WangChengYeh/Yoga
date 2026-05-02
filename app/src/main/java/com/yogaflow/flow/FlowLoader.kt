package com.yogaflow.flow

import android.content.Context

object FlowLoader {

    fun loadFromAssets(context: Context, fileName: String): YogaFlow {
        val text = context.assets.open(fileName).bufferedReader().use { it.readText() }
        return FlowParser.parse(text)
    }
}
