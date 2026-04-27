package com.example.visa.util

import com.example.visa.dataclasses.Configuration
import android.content.Context
import org.json.JSONObject

object ConfigurationLoader {

    fun load(
        context: Context,
        fileName: String = "config.json",
        strictMode: Boolean = false,
        warningMode: Boolean = true
    ): Configuration {
        val jsonString = context.assets
            .open(fileName)
            .bufferedReader()
            .use { it.readText() }

        val jsonObject = JSONObject(jsonString)
        val dataMap = jsonToMap(jsonObject)

        return Configuration(
            data = dataMap,
            strictMode = strictMode,
            warningMode = warningMode
        )
    }

    private fun jsonToMap(jsonObject: JSONObject): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()

        val keys = jsonObject.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = jsonObject.get(key)

            map[key] = when (value) {
                is JSONObject -> jsonToMap(value)
                JSONObject.NULL -> null
                else -> value
            }
        }

        return map
    }
}