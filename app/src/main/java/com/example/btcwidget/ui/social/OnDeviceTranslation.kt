package com.example.btcwidget.ui.social

import com.google.android.gms.tasks.Tasks
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions

/** Detects and translates note text entirely on the device. Language models are downloaded on demand. */
object OnDeviceTranslation {
    fun targetLanguage(): String = if (Lang.thai) "th" else "en"

    fun translate(text: String, targetTag: String): String {
        val sourceTag = Tasks.await(LanguageIdentification.getClient().identifyLanguage(text))
        if (sourceTag == "und") error("Couldn't identify the language")
        if (sourceTag == targetTag) return text
        val source = TranslateLanguage.fromLanguageTag(sourceTag) ?: error("Language not supported for translation")
        val target = TranslateLanguage.fromLanguageTag(targetTag) ?: error("Language not supported for translation")
        val translator = Translation.getClient(
            TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build()
        )
        return try {
            Tasks.await(translator.downloadModelIfNeeded(DownloadConditions.Builder().build()))
            Tasks.await(translator.translate(text))
        } finally {
            translator.close()
        }
    }
}
