package com.dentalstudio.notes.ui.nav

object Routes {
    const val HOME = "home"
    const val RECORD = "record"
    const val NOTE = "note/{noteId}"
    const val ADAPT = "adapt"
    const val SETTINGS = "settings"

    fun note(id: Long) = "note/$id"
}
