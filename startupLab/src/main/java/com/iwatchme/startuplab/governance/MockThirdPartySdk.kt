package com.iwatchme.startuplab.governance

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import com.iwatchme.startuplab.core.StartupLog
import com.iwatchme.startuplab.core.StartupMode
import com.iwatchme.startuplab.core.StartupModeStore
import com.iwatchme.startuplab.scenario.StartupScenarioCatalog

data class ThirdPartySdkSnapshot(
    val legacyProviderInitMs: Long = 0L,
    val startupBridgeInitMs: Long = 0L,
    val governanceInitializerMs: Long = 0L,
    val deferredInitializerMs: Long = 0L,
    val initSource: String = "pending",
    val governanceState: String = "legacy-provider",
)

object ThirdPartySdkTracker {
    @Volatile
    private var legacyProviderInitMs: Long = 0L

    @Volatile
    private var startupBridgeInitMs: Long = 0L

    @Volatile
    private var governanceInitializerMs: Long = 0L

    @Volatile
    private var deferredInitializerMs: Long = 0L

    @Volatile
    private var initSource: String = "pending"

    @Volatile
    private var governanceState: String = "legacy-provider"

    fun recordLegacyProvider(durationMs: Long) {
        legacyProviderInitMs = durationMs
        initSource = "legacy-provider"
        governanceState = "heavy-provider-ran-at-startup"
    }

    fun recordProviderBypassed() {
        legacyProviderInitMs = 0L
        initSource = "initialization-provider"
        governanceState = "legacy-provider-bypassed"
    }

    fun recordStartupBridge(durationMs: Long) {
        startupBridgeInitMs = durationMs
    }

    fun recordGovernanceInitializer(durationMs: Long) {
        governanceInitializerMs = durationMs
        initSource = "app-startup-governance"
        governanceState = "governed-and-deferred"
    }

    fun recordDeferredInitializer(durationMs: Long) {
        deferredInitializerMs = durationMs
        initSource = "deferred-initializer"
        governanceState = "deferred-init-completed"
    }

    fun snapshot(): ThirdPartySdkSnapshot {
        return ThirdPartySdkSnapshot(
            legacyProviderInitMs = legacyProviderInitMs,
            startupBridgeInitMs = startupBridgeInitMs,
            governanceInitializerMs = governanceInitializerMs,
            deferredInitializerMs = deferredInitializerMs,
            initSource = initSource,
            governanceState = governanceState,
        )
    }
}

class MockHeavySdkProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        val appContext = context ?: return true
        val mode = StartupModeStore.currentMode(appContext)
        if (mode == StartupMode.LEGACY) {
            val start = System.nanoTime()
            Thread.sleep(StartupScenarioCatalog.legacyHeavyProviderCostMs)
            val durationMs = (System.nanoTime() - start) / 1_000_000L
            ThirdPartySdkTracker.recordLegacyProvider(durationMs)
            StartupLog.d("Heavy SDK ContentProvider init duration=${durationMs}ms")
        } else {
            ThirdPartySdkTracker.recordProviderBypassed()
            StartupLog.d("Heavy SDK ContentProvider bypassed; App Startup will govern deferred initialization")
        }
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
