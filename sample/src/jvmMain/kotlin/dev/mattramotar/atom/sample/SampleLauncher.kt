package dev.mattramotar.atom.sample

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Atom Sample Showcase"
    ) {
        MaterialTheme {
            SampleShowcaseApp()
        }
    }
}
