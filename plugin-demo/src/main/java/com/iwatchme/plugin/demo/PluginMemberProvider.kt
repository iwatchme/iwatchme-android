package com.iwatchme.plugin.demo

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.util.Log

/**
 * 插件 ContentProvider 示例：固定返回 3 条会员数据。
 *
 * 验证：Shadow 的 `PluginContainerContentProvider` 壳子能根据 authority 路由
 * `content://com.iwatchme.android.shadow.provider.dynamic/...` 到插件这个 Provider 类。
 *
 * 注意 authority 用 plugin 自己的命名空间；Shadow 在 [com.iwatchme.plugin.loader.IwatchmeComponentManager]
 * 把它转写成 `${host.applicationId}.shadow.provider.dynamic` 这个壳子 authority。
 */
class PluginMemberProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        Log.i(TAG, "onCreate in plugin process pid=${android.os.Process.myPid()}")
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        Log.i(TAG, "query uri=$uri selection=$selection")
        return MatrixCursor(arrayOf("_id", "name", "level")).apply {
            addRow(arrayOf<Any>(1, "alice", "gold"))
            addRow(arrayOf<Any>(2, "bob", "silver"))
            addRow(arrayOf<Any>(3, "carol", "bronze"))
        }
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.dir/vnd.iwatchme.plugin.member"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        const val AUTHORITY = "com.iwatchme.plugin.demo.members"
        const val TAG = "PluginMemberProvider"
        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/members")
    }
}
