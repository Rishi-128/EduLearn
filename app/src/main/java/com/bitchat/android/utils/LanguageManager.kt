package com.bitchat.android.utils

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Manages language preferences and switching for the educational mode
 * Supports English, Hindi, and Punjabi
 */
object LanguageManager {
    
    private const val PREFS_NAME = "language_prefs"
    private const val KEY_LANGUAGE = "selected_language"
    
    // Available languages
    enum class Language(val code: String, val displayName: String, val nativeName: String) {
        ENGLISH("en", "English", "English"),
        HINDI("hi", "Hindi", "हिंदी"),
        PUNJABI("pa", "Punjabi", "ਪੰਜਾਬੀ")
    }
    
    private val _currentLanguage = MutableStateFlow(Language.ENGLISH)
    val currentLanguage: StateFlow<Language> = _currentLanguage.asStateFlow()
    
    @Volatile
    private var prefs: SharedPreferences? = null
    private var isInitialized = false
    
    /**
     * Initialize language manager with context
     */
    fun initialize(context: Context) {
        if (isInitialized) return
        synchronized(this) {
            if (isInitialized) return
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            loadSavedLanguage()
            isInitialized = true
        }
    }
    
    /**
     * Load saved language from preferences
     */
    private fun loadSavedLanguage() {
        val savedCode = prefs?.getString(KEY_LANGUAGE, Language.ENGLISH.code) ?: Language.ENGLISH.code
        _currentLanguage.value = Language.entries.find { it.code == savedCode } ?: Language.ENGLISH
    }
    
    /**
     * Get current language
     */
    fun getCurrentLanguage(): Language {
        return _currentLanguage.value
    }
    
    /**
     * Switch to a different language
     */
    fun setLanguage(language: Language, context: Context) {
        _currentLanguage.value = language
        // Use commit() instead of apply() to ensure synchronous save before activity recreation
        prefs?.edit()?.putString(KEY_LANGUAGE, language.code)?.commit()
        
        // Update app locale
        updateLocale(context, language.code)
    }
    
    /**
     * Toggle between available languages
     */
    fun toggleLanguage(context: Context) {
        val nextLanguage = when (_currentLanguage.value) {
            Language.ENGLISH -> Language.HINDI
            Language.HINDI -> Language.PUNJABI
            Language.PUNJABI -> Language.ENGLISH
        }
        setLanguage(nextLanguage, context)
    }
    
    /**
     * Update system locale for the app
     */
    private fun updateLocale(context: Context, languageCode: String) {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        
        context.resources.updateConfiguration(configuration, context.resources.displayMetrics)
    }
    
    /**
     * Get all available languages
     */
    fun getAvailableLanguages(): List<Language> {
        return Language.entries.toList()
    }
    
    /**
     * Create context wrapper with specific locale (for activities)
     */
    fun wrapContext(context: Context, language: Language): Context {
        val locale = Locale(language.code)
        Locale.setDefault(locale)
        
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        
        return context.createConfigurationContext(configuration)
    }
}
