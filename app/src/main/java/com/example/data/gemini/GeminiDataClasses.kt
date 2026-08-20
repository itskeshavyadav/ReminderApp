package com.example.data.gemini

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GeminiGenerateRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig? = null,
    val systemInstruction: GeminiContent? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    val role: String = "user",
    val parts: List<GeminiPart>
)

@JsonClass(generateAdapter = true)
data class GeminiPart(
    val text: String
)

@JsonClass(generateAdapter = true)
data class GeminiGenerationConfig(
    val temperature: Float? = 0.2f,
    val topP: Float? = 0.95f,
    val responseMimeType: String? = null
)

@JsonClass(generateAdapter = true)
data class GeminiGenerateResponse(
    val candidates: List<GeminiCandidate>? = null
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    val content: GeminiCandidateContent? = null
)

@JsonClass(generateAdapter = true)
data class GeminiCandidateContent(
    val parts: List<GeminiPart>? = null
)

@JsonClass(generateAdapter = true)
data class ParsedTaskDto(
    val title: String,
    val note: String? = "",
    val category: String? = "GENERAL",
    val priority: String? = "MEDIUM",
    val dueOffsetMinutes: Long? = 60,
    val isNagging: Boolean? = false,
    val naggingIntervalMinutes: Int? = 5,
    val recurrenceType: String? = "NONE",
    val recurrenceInterval: Int? = 1,
    val reason: String? = "Parsed from voice command"
)

@JsonClass(generateAdapter = true)
data class SmartSuggestionDto(
    val title: String,
    val note: String? = "",
    val category: String? = "GENERAL",
    val priority: String? = "MEDIUM",
    val dueOffsetMinutes: Long? = 120,
    val isNagging: Boolean? = false,
    val naggingIntervalMinutes: Int? = 5,
    val recurrenceType: String? = "NONE",
    val recurrenceInterval: Int? = 1,
    val reason: String
)
