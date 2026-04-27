package com.example.visa

import android.content.Context
import com.example.visa.analyzer.VisualAnalyzer
import com.example.visa.dataclasses.*
import com.example.visa.util.ConfigurationLoader

object AppContainer {
    lateinit var config: Configuration
    lateinit var visualAnalyzer: VisualAnalyzer

    fun init(context: Context) {
        config = ConfigurationLoader.load(context)

        visualAnalyzer = VisualAnalyzer(
            ocrName = config.getString("ocr.name", "EasyOCR"),
            confidenceThreshold = config.getFloat("ocr.minConfidence", 0.5f),
            mergeDistanceThreshold_y = config.getInt("ocr.mergeDistanceThreshold_y", 20),
            mergeDistanceThreshold_x = config.getInt("ocr.mergeDistanceThreshold_x", 4),
            modelName = config.getString("model.name", "gemma3:4b")
        )
    }
}