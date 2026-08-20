package com.example.data.gemini

import android.util.Log
import com.example.BuildConfig
import com.example.data.model.Priority
import com.example.data.model.RecurrenceType
import com.example.data.model.Reminder
import com.example.data.model.ReminderCategory
import com.example.data.model.SmartSuggestion
import com.example.data.model.SuggestionSource
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.Calendar
import java.util.concurrent.TimeUnit

interface GeminiApi {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiGenerateRequest
    ): GeminiGenerateResponse
}

class GeminiReminderService {

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.NONE
        })
        .build()

    private val geminiApi: GeminiApi = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(GeminiApi::class.java)

    /**
     * Parses complex voice/text commands using Gemini API with structured JSON output,
     * seamlessly falling back to on-device OfflineSmartEngine if offline or error occurs.
     */
    suspend fun parseVoiceOrTextCommand(rawInput: String): OfflineSmartEngine.ParsedTask = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            Log.d("GeminiService", "Using local offline engine for NLP command")
            return@withContext OfflineSmartEngine.parseNaturalLanguage(rawInput)
        }

        val systemPrompt = """
            You are a task & reminder extraction parser.
            Return ONLY a valid JSON object matching this schema:
            {
              "title": "Clean, concise reminder title",
              "note": "Optional details or context",
              "category": "GENERAL" | "WORK" | "HEALTH" | "PERSONAL" | "HABIT" | "FINANCES",
              "priority": "LOW" | "MEDIUM" | "HIGH" | "URGENT",
              "dueOffsetMinutes": 60,
              "isNagging": true | false,
              "naggingIntervalMinutes": 5,
              "recurrenceType": "NONE" | "DAILY" | "WEEKDAYS" | "WEEKLY" | "MONTHLY" | "CUSTOM_HOURS" | "CUSTOM_DAYS",
              "recurrenceInterval": 1,
              "reason": "Extraction summary"
            }
            Do not include markdown wrappers or extra commentary.
        """.trimIndent()

        val userPrompt = "Input sentence: \"$rawInput\". Current time: ${Calendar.getInstance().time}. Parse into reminder JSON."

        val request = GeminiGenerateRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(GeminiPart(text = userPrompt))
                )
            ),
            systemInstruction = GeminiContent(
                parts = listOf(GeminiPart(text = systemPrompt))
            ),
            generationConfig = GeminiGenerationConfig(
                temperature = 0.1f,
                responseMimeType = "application/json"
            )
        )

        try {
            val response = geminiApi.generateContent(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (!jsonText.isNullOrBlank()) {
                val adapter = moshi.adapter(ParsedTaskDto::class.java)
                val dto = adapter.fromJson(jsonText.trim())
                if (dto != null) {
                    val cal = Calendar.getInstance()
                    cal.add(Calendar.MINUTE, (dto.dueOffsetMinutes ?: 60).toInt())
                    return@withContext OfflineSmartEngine.ParsedTask(
                        title = dto.title.ifBlank { rawInput },
                        note = dto.note ?: "",
                        category = parseCategory(dto.category),
                        priority = parsePriority(dto.priority),
                        dueTimestamp = cal.timeInMillis,
                        isNagging = dto.isNagging ?: false,
                        naggingIntervalMinutes = dto.naggingIntervalMinutes ?: 5,
                        recurrenceType = parseRecurrence(dto.recurrenceType),
                        recurrenceInterval = dto.recurrenceInterval ?: 1
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("GeminiService", "Gemini parsing error, falling back to offline engine: ${e.message}")
        }

        // Fallback to offline engine
        OfflineSmartEngine.parseNaturalLanguage(rawInput)
    }

    /**
     * Generates intelligent schedule suggestions using Gemini AI when enabled,
     * blended with offline schedule context.
     */
    suspend fun generateSmartSuggestions(
        existingReminders: List<Reminder>,
        aiEnabled: Boolean
    ): List<SmartSuggestion> = withContext(Dispatchers.IO) {
        val offlineSuggestions = OfflineSmartEngine.generateOfflineSuggestions(existingReminders)
        
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (!aiEnabled || apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext offlineSuggestions
        }

        val existingSummary = existingReminders.take(10).joinToString("; ") {
            "${it.title} [${it.category}, ${if (it.isCompleted) "completed" else "pending"}]"
        }

        val prompt = """
            Analyze the user's current schedule patterns and recent activity:
            Current reminders: $existingSummary
            Current time: ${Calendar.getInstance().time}
            
            Suggest 2-3 personalized, non-intrusive smart reminders (e.g. hydration, focus, breaks, recurring habits).
            Return a valid JSON array of objects with keys:
            [
              {
                "title": "Title",
                "note": "Description",
                "category": "WORK" | "HEALTH" | "PERSONAL" | "HABIT" | "FINANCES" | "GENERAL",
                "priority": "LOW" | "MEDIUM" | "HIGH" | "URGENT",
                "dueOffsetMinutes": 90,
                "isNagging": false,
                "naggingIntervalMinutes": 5,
                "recurrenceType": "NONE" | "DAILY" | "WEEKLY" | "MONTHLY" | "CUSTOM_HOURS" | "CUSTOM_DAYS",
                "recurrenceInterval": 1,
                "reason": "Why this suggestion fits the schedule"
              }
            ]
        """.trimIndent()

        val request = GeminiGenerateRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(GeminiPart(text = prompt))
                )
            ),
            generationConfig = GeminiGenerationConfig(
                temperature = 0.3f,
                responseMimeType = "application/json"
            )
        )

        try {
            val response = geminiApi.generateContent(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (!jsonText.isNullOrBlank()) {
                val listType = com.squareup.moshi.Types.newParameterizedType(List::class.java, SmartSuggestionDto::class.java)
                val adapter = moshi.adapter<List<SmartSuggestionDto>>(listType)
                val dtoList = adapter.fromJson(jsonText.trim())
                if (!dtoList.isNullOrEmpty()) {
                    val aiSuggestions = dtoList.map { dto ->
                        val cal = Calendar.getInstance()
                        cal.add(Calendar.MINUTE, (dto.dueOffsetMinutes ?: 90).toInt())
                        SmartSuggestion(
                            title = dto.title,
                            note = dto.note ?: "",
                            suggestedTimestamp = cal.timeInMillis,
                            category = parseCategory(dto.category),
                            priority = parsePriority(dto.priority),
                            isNagging = dto.isNagging ?: false,
                            naggingIntervalMinutes = dto.naggingIntervalMinutes ?: 5,
                            recurrenceType = parseRecurrence(dto.recurrenceType),
                            recurrenceInterval = dto.recurrenceInterval ?: 1,
                            reason = dto.reason,
                            source = SuggestionSource.GEMINI_AI
                        )
                    }
                    return@withContext (aiSuggestions + offlineSuggestions).distinctBy { it.title.lowercase() }.take(5)
                }
            }
        } catch (e: Exception) {
            Log.w("GeminiService", "Gemini suggestion fetch error: ${e.message}")
        }

        offlineSuggestions
    }

    private fun parseCategory(cat: String?): ReminderCategory {
        return try {
            ReminderCategory.valueOf(cat?.uppercase() ?: "GENERAL")
        } catch (e: Exception) {
            ReminderCategory.GENERAL
        }
    }

    private fun parsePriority(p: String?): Priority {
        return try {
            Priority.valueOf(p?.uppercase() ?: "MEDIUM")
        } catch (e: Exception) {
            Priority.MEDIUM
        }
    }

    private fun parseRecurrence(r: String?): RecurrenceType {
        return try {
            RecurrenceType.valueOf(r?.uppercase() ?: "NONE")
        } catch (e: Exception) {
            RecurrenceType.NONE
        }
    }
}
