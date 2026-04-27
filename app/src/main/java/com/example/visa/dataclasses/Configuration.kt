package com.example.visa.dataclasses

import kotlin.collections.get

class Configuration(
    private val data: Map<String, Any?>,
    private val strictMode: Boolean = false,
    private val warningMode: Boolean = true
) {
    fun getString(name: String, default: String = ""): String {
        return getValue(name, default) as? String ?: default
    }

    fun getInt(name: String, default: Int = 0): Int {
        return when (val value = getValue(name, default)) {
            is Int -> value
            is Double -> value.toInt()
            is String -> value.toIntOrNull() ?: default
            else -> default
        }
    }

    fun getFloat(name: String, default: Float = 0.0f): Float {
        return when (val value = getValue(name, default)) {
            is Float -> value
            is Double -> value.toFloat()
            is Int -> value.toFloat()
            is String -> value.toFloatOrNull() ?: default
            else -> default
        }
    }

    fun getBoolean(name: String, default: Boolean = false): Boolean {
        return when (val value = getValue(name, default)) {
            is Boolean -> value
            is Int -> value > 0
            is String -> value == "true" || value == "1"
            else -> default
        }
    }

    fun contains(name: String): Boolean {
        return findKey(name) != null
    }

    private fun getValue(name: String, default: Any?): Any? {
        val value = findKey(name)

        if (value != null) {
            return value
        }

        if (strictMode) {
            throw IllegalArgumentException("Key $name not found in configuration")
        }

        if (warningMode) {
            println("- WARNING: Key $name not found, using default value: $default")
        }

        return default
    }

    private fun findKey(fullKey: String): Any? {
        val parts = fullKey.split(".")
        var current: Any? = data

        for (part in parts) {
            current = if (current is Map<*, *>) {
                current[part]
            } else {
                return null
            }
        }

        return current
    }
}