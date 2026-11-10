package com.oyayasu.ankivideoplayer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent

object AnkiBridge {
    fun send(context: Context, entry: WordEntry) {
        val front = entry.word + "\n" + entry.subtitle
        val back = entry.meanings.mapIndexed { index, meaning ->
            (index + 1).toString() + ". " + meaning.definition + " (" +
                meaning.partOfSpeech + ") " + meaning.ipa
        }.joinToString("\n")
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, front)
            putExtra(Intent.EXTRA_TEXT, back)
            setPackage("com.ichi2.anki")
        }
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            intent.setPackage(null)
            context.startActivity(Intent.createChooser(intent, "Enviar cartão"))
        }
    }
}
