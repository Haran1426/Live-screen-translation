package com.example.livescreentranslation.capture

import com.example.livescreentranslation.privacy.NativeLogPrivacy
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions

object ModelRepository {
    fun translator(config: CaptureConfig): Translator {
        NativeLogPrivacy.requireProtection()
        return Translation.getClient(
            TranslatorOptions.Builder().setSourceLanguage(config.source)
                .setTargetLanguage(config.target).build(),
        )
    }

    fun ready(config: CaptureConfig): Task<Boolean> {
        val manager = RemoteModelManager.getInstance()
        val checks = setOf(config.source, config.target).filter { it != "en" }.map {
            manager.isModelDownloaded(TranslateRemoteModel.Builder(it).build())
        }
        return Tasks.whenAllSuccess<Boolean>(checks).continueWith { task ->
            task.result.all { it }
        }
    }

    fun prepare(config: CaptureConfig): Task<Void> {
        val translator = translator(config)
        val conditions = DownloadConditions.Builder().apply {
            if (config.wifiOnly) requireWifi()
        }.build()
        return translator.downloadModelIfNeeded(conditions)
            .addOnCompleteListener { translator.close() }
    }
}
