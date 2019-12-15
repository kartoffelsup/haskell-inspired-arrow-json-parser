package io.github.kartoffelsup.json

sealed class JsonValue

object JsonNull : JsonValue() {
    override fun toString() =
        "JsonNull"
}

data class JsonBool(val value: Boolean) : JsonValue()
// TODO: no support for floats
data class JsonNumber(val value: Int) : JsonValue()
data class JsonString(val value: String) : JsonValue()
data class JsonArray(val value: List<JsonValue>) : JsonValue()
data class JsonObject(val value: Map<String, JsonValue>) : JsonValue()