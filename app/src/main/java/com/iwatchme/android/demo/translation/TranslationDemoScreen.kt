package com.iwatchme.android.demo.translation

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.iwatchme.android.BuildConfig
import io.ai.sdk.translation.TranslationEngine
import io.ai.sdk.translation.cloudflare.CloudflareTranslationSdk
import kotlinx.coroutines.launch

private const val TAG = "TranslationDemo"

private const val DEFAULT_TEXT = "Hello, this is a translation demo. Welcome to try different languages."

@Composable
fun TranslationDemoScreen(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()

    var inputText by remember { mutableStateOf(DEFAULT_TEXT) }
    var resultText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var sourceLang by remember { mutableStateOf("english") }
    var targetLang by remember { mutableStateOf("chinese") }
    var showSourceMenu by remember { mutableStateOf(false) }
    var showTargetMenu by remember { mutableStateOf(false) }

    var engine by remember { mutableStateOf<TranslationEngine?>(null) }

    fun getOrCreateEngine(): TranslationEngine {
        return engine ?: TranslationEngine.Builder()
            .sdk(
                CloudflareTranslationSdk(
                    accountId = BuildConfig.CLOUDFLARE_ACCOUNT_ID,
                    apiToken = BuildConfig.CLOUDFLARE_API_TOKEN,
                )
            )
            .build()
            .also { engine = it }
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
            text = "Cloudflare M2M100 Translation",
            style = MaterialTheme.typography.h6,
        )

        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            label = { Text("Text to translate") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 5,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Source", style = MaterialTheme.typography.caption)
                OutlinedButton(onClick = { showSourceMenu = true }) {
                    Text(sourceLang.replaceFirstChar { it.uppercase() })
                }
                DropdownMenu(expanded = showSourceMenu, onDismissRequest = { showSourceMenu = false }) {
                    CloudflareTranslationSdk.SUPPORTED_LANGUAGES.forEach { lang ->
                        DropdownMenuItem(onClick = { sourceLang = lang; showSourceMenu = false }) {
                            Text(lang.replaceFirstChar { it.uppercase() })
                        }
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text("Target", style = MaterialTheme.typography.caption)
                OutlinedButton(onClick = { showTargetMenu = true }) {
                    Text(targetLang.replaceFirstChar { it.uppercase() })
                }
                DropdownMenu(expanded = showTargetMenu, onDismissRequest = { showTargetMenu = false }) {
                    CloudflareTranslationSdk.SUPPORTED_LANGUAGES.forEach { lang ->
                        DropdownMenuItem(onClick = { targetLang = lang; showTargetMenu = false }) {
                            Text(lang.replaceFirstChar { it.uppercase() })
                        }
                    }
                }
            }
        }

        Button(
            onClick = {
                if (inputText.isBlank()) {
                    errorMessage = "Please enter some text"
                    return@Button
                }
                isLoading = true
                errorMessage = null
                resultText = ""
                scope.launch {
                    try {
                        val eng = getOrCreateEngine()
                        val result = eng.translate(inputText, sourceLang, targetLang)
                        if (result.isSuccess) {
                            resultText = result.translatedText
                        } else {
                            errorMessage = result.error?.message ?: "Translation failed"
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "translate error", e)
                        errorMessage = e.message ?: "Unknown error"
                    } finally {
                        isLoading = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 8.dp),
                    color = MaterialTheme.colors.onPrimary,
                    strokeWidth = 2.dp,
                )
            }
            Text("Translate")
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
            Text(
                text = resultText,
                style = MaterialTheme.typography.body1,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
            )
        }
    }
}
