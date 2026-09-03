package com.dentalstudio.notes.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.dentalstudio.notes.desktop.speech.DesktopDictation
import com.dentalstudio.notes.desktop.store.DesktopRepository
import com.dentalstudio.notes.desktop.ui.App
import com.dentalstudio.notes.desktop.ui.DentalNotesTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers

fun main() = application {
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    val dictation = remember { DesktopDictation() }
    val model = remember { AppModel(DesktopRepository(), dictation, scope) }

    val windowState = rememberWindowState(size = DpSize(1280.dp, 840.dp))

    Window(
        onCloseRequest = {
            dictation.release()
            scope.cancel()
            exitApplication()
        },
        state = windowState,
        title = "Dental Notes",
    ) {
        window.minimumSize = java.awt.Dimension(1040, 680)
        DentalNotesTheme {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                App(model)
            }
        }
    }
}
