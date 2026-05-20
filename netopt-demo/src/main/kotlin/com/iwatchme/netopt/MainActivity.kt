package com.iwatchme.netopt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.iwatchme.netopt.ui.Experiment
import com.iwatchme.netopt.ui.ExperimentDetailScreen
import com.iwatchme.netopt.ui.ExperimentE2Screen
import com.iwatchme.netopt.ui.ExperimentE3Screen
import com.iwatchme.netopt.ui.ExperimentE5Screen
import com.iwatchme.netopt.ui.ExperimentE6Screen
import com.iwatchme.netopt.ui.ExperimentE7Screen
import com.iwatchme.netopt.ui.ExperimentE8Screen
import com.iwatchme.netopt.ui.ExperimentE9Screen
import com.iwatchme.netopt.ui.ExperimentE10Screen
import com.iwatchme.netopt.ui.ExperimentE11Screen
import com.iwatchme.netopt.ui.ExperimentE12Screen
import com.iwatchme.netopt.ui.ExperimentsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var selected by remember { mutableStateOf<Experiment?>(null) }
                    val sel = selected
                    if (sel == null) {
                        ExperimentsScreen(onExperimentClick = { selected = it })
                    } else when (sel.id) {
                        "E2" -> ExperimentE2Screen(onBack = { selected = null })
                        "E3" -> ExperimentE3Screen(onBack = { selected = null })
                        "E5" -> ExperimentE5Screen(onBack = { selected = null })
                        "E6" -> ExperimentE6Screen(onBack = { selected = null })
                        "E7" -> ExperimentE7Screen(onBack = { selected = null })
                        "E8" -> ExperimentE8Screen(onBack = { selected = null })
                        "E9" -> ExperimentE9Screen(onBack = { selected = null })
                        "E10" -> ExperimentE10Screen(onBack = { selected = null })
                        "E11" -> ExperimentE11Screen(onBack = { selected = null })
                        "E12" -> ExperimentE12Screen(onBack = { selected = null })
                        else -> ExperimentDetailScreen(
                            experimentId = sel.id,
                            title = sel.title,
                            onBack = { selected = null }
                        )
                    }
                }
            }
        }
    }
}
