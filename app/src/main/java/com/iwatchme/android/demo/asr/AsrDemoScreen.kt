package com.iwatchme.android.demo.asr

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.iwatchme.android.BuildConfig
import io.ai.sdk.asr.AsrEngine
import io.ai.sdk.asr.cloudflare.CloudflareAsrSdk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "AsrDemo"

@Composable
fun AsrDemoScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("") }
    var resultText by remember { mutableStateOf("") }
    var wordCount by remember { mutableStateOf<Int?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var engine by remember { mutableStateOf<AsrEngine?>(null) }

    fun getOrCreateEngine(): AsrEngine {
        return engine ?: AsrEngine.Builder()
            .sdk(
                CloudflareAsrSdk(
                    accountId = BuildConfig.CLOUDFLARE_ACCOUNT_ID,
                    apiToken = BuildConfig.CLOUDFLARE_API_TOKEN,
                )
            )
            .build()
            .also { engine = it }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedUri = it
            selectedFileName = it.lastPathSegment ?: "audio file"
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            scope.launch { engine?.close() }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Cloudflare Whisper ASR",
            style = MaterialTheme.typography.h6,
        )

        Button(
            onClick = { filePicker.launch("audio/*") },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Select Audio File")
        }

        if (selectedFileName.isNotBlank()) {
            Text(
                text = "Selected: $selectedFileName",
                style = MaterialTheme.typography.body2,
            )
        }

        Button(
            onClick = {
                val uri = selectedUri
                if (uri == null) {
                    errorMessage = "Please select an audio file first"
                    return@Button
                }
                isLoading = true
                errorMessage = null
                resultText = ""
                wordCount = null
                scope.launch {
                    try {
                        val audioData = withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                ?: throw RuntimeException("Cannot read audio file")
                        }
                        val eng = getOrCreateEngine()
                        val result = eng.recognize(audioData)
                        if (result.isSuccess) {
                            resultText = result.text
                            wordCount = result.wordCount
                        } else {
                            errorMessage = result.error?.message ?: "Recognition failed"
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "recognize error", e)
                        errorMessage = e.message ?: "Unknown error"
                    } finally {
                        isLoading = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading && selectedUri != null,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 8.dp),
                    color = MaterialTheme.colors.onPrimary,
                    strokeWidth = 2.dp,
                )
            }
            Text("Recognize")
        }

        if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colors.error,
                style = MaterialTheme.typography.caption,
            )
        }

        if (resultText.isNotBlank()) {
            Text("Result:", style = MaterialTheme.typography.subtitle1)
            if (wordCount != null) {
                Text("Word count: $wordCount", style = MaterialTheme.typography.caption)
            }
            OutlinedTextField(
                value = resultText,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 10,
            )
        }
    }
}
