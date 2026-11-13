package com.github.andreyasadchy.xtra.lab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Entry point for the U0 technical spikes. Started with `am start -n <pkg>/com.github.andreyasadchy.xtra.lab.LabActivity`. */
class LabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LabHome() }
    }
}

@Composable
fun LabHome() {
    Column(Modifier.fillMaxSize().background(Color(0xFF111113)).padding(16.dp)) {
        Text("Lagoon lab", color = Color.White)
    }
}
