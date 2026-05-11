package com.yogaflow.avatar

import org.json.JSONObject

data class AvatarIntent(
    val action: String,
    val emotion: String = "calm",
    val highlight: String? = null,
    val position: String = "demo_area",
    val facing: String = "user",
    val scale: Float = 1.0f,
    val overridePosition: Pair<Float, Float>? = null
) {
    fun toJson(): JSONObject = JSONObject()
        .put("action", action)
        .put("emotion", emotion)
        .putNullable("highlight", highlight)
        .put("position", position)
        .put("facing", facing)
        .put("scale", scale.toDouble())
        .apply {
            overridePosition?.let { (x, y) ->
                put("override_position", JSONObject().put("x", x.toDouble()).put("y", y.toDouble()))
            }
        }
}

private fun JSONObject.putNullable(name: String, value: Any?): JSONObject {
    return put(name, value ?: JSONObject.NULL)
}
