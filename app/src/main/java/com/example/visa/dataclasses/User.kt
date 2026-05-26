package com.example.visa.dataclasses

class User {
    var goal: String? = null
        private set

    var inputText: String? = null
        private set

    var voiceEnabled: Boolean = false
        private set

    fun setGoal(goal:String?) {
        this.goal = goal?.trim()
    }

    fun setInputText(inputText: String?) {
        this.inputText = inputText?.trim()
    }

    fun clearGoal() {
        goal = null
    }

    fun clearInputText() {
        inputText = null
    }

    fun setVoiceEnabled(enabled: Boolean) {
        this.voiceEnabled = enabled
    }
}