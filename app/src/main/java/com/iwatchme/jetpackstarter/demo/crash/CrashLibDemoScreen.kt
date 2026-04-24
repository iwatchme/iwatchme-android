package com.iwatchme.jetpackstarter.demo.crash

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.iwatchme.crashlib.CrashLib
import com.iwatchme.crashlib.ICrashCallback
import com.iwatchme.crashlib.SignalConst
import kotlin.concurrent.thread

@Composable
fun CrashLibDemoScreen(modifier: Modifier = Modifier) {
    var isInitialized by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("CrashLib is not initialized") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "CrashLib Demo")
        Text(text = statusText)

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                CrashLib.initCrashSdk(
                    signals = intArrayOf(
                        SignalConst.SIGABRT,
                        SignalConst.SIGSEGV,
                    ),
                    crashCallback = object : ICrashCallback {
                        override fun checkIsAnr(): Boolean = false

                        override fun onHandleAnr() {
                            Log.e("CrashLibDemo", "ANR captured")
                        }

                        override fun onHandleCrash(signal: Int, nativeStackTrace: String) {
                            Log.e("CrashLibDemo", "signal=$signal\n$nativeStackTrace")
                        }
                    },
                )
                isInitialized = true
                statusText = "CrashLib initialized"
            },
        ) {
            Text(text = "Initialize CrashLib")
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (!isInitialized) {
                    statusText = "Initialize CrashLib first"
                    return@Button
                }
                statusText = "Triggering native crash on background thread..."
                thread(name = "CrashLib-Demo-Worker") {
                    CrashLib.raiseError()
                }
            },
        ) {
            Text(text = "Trigger Native Crash")
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                statusText = "Triggering Java crash on main thread..."
                throw RuntimeException("Manual Java crash from CrashLib demo")
            },
        ) {
            Text(text = "Trigger Java Crash")
        }
    }
}
