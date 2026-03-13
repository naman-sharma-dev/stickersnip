package com.stickersnip.app

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File

class StickerContentProvider : ContentProvider() {

    companion object {
        private const val METADATA = 1
        private const val METADATA_CODE = "metadata"
        private const val STICKERS = 2
        private const val STICKERS_CODE = "stickers"
        private const val STICKER_FILE = 3
        private const val STICKER_ASSET = 4

        fun getAuthority(ctx: android.content.Context): String {
            return "${ctx.packageName}.stickercontentprovider"
        }

        fun getContentUri(ctx: android.content.Context): Uri {
            return Uri.parse("content://${getAuthority(ctx)}")
        }
    }

    private lateinit var authority: String
    private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH)

    override fun onCreate(): Boolean {
        authority = "${context!!.packageName}.stickercontentprovider"
        uriMatcher.addURI(authority, METADATA_CODE, METADATA)
        uriMatcher.addURI(authority, "stickers/*", STICKERS)
        uriMatcher.addURI(authority, "stickers_asset/*/tray.png", STICKER_FILE)
        uriMatcher.addURI(authority, "stickers_asset/*/*", STICKER_ASSET)
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val ctx = context ?: return null
        return when (uriMatcher.match(uri)) {
            METADATA -> {
                getPackListCursor(ctx)
            }
            STICKERS -> {
                val packId = uri.lastPathSegment ?: return null
                getStickerListCursor(ctx, packId)
            }
            else -> null
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val ctx = context ?: return null
        val segments = uri.pathSegments
        if (segments.size < 3) return null

        val packId = segments[1]

        return when (uriMatcher.match(uri)) {
            STICKER_FILE -> {
                val file = PackManager.getTrayFile(ctx, packId)
                if (file.exists()) {
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                } else null
            }
            STICKER_ASSET -> {
                val fileName = segments[2]
                val file = PackManager.getStickerFile(ctx, packId, fileName)
                if (file.exists()) {
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                } else null
            }
            else -> null
        }
    }

    override fun getType(uri: Uri): String {
        return when (uriMatcher.match(uri)) {
            METADATA -> "vnd.android.cursor.dir/vnd.$authority.metadata"
            STICKERS -> "vnd.android.cursor.dir/vnd.$authority.stickers"
            STICKER_FILE -> "image/png"
            STICKER_ASSET -> "image/webp"
            else -> "application/octet-stream"
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private fun getPackListCursor(ctx: android.content.Context): Cursor {
        val columns = arrayOf(
            "sticker_pack_identifier",
            "sticker_pack_name",
            "sticker_pack_publisher",
            "sticker_pack_icon",
            "android_play_store_link",
            "ios_app_store_link",
            "publisher_email",
            "publisher_website",
            "privacy_policy_website",
            "license_agreement_website",
            "image_data_version",
            "avoid_cache",
            "animated_sticker_pack"
        )
        val cursor = MatrixCursor(columns)
        val packs = PackManager.loadAllPacks(ctx)
        for (pack in packs) {
            cursor.addRow(arrayOf(
                pack.id,
                pack.name,
                pack.publisher,
                pack.trayImageFile,
                "",
                "",
                "",
                "",
                "",
                "",
                "1",
                "false",
                "false"
            ))
        }
        return cursor
    }

    private fun getStickerListCursor(ctx: android.content.Context, packId: String): Cursor {
        val columns = arrayOf(
            "sticker_file_name",
            "sticker_emoji"
        )
        val cursor = MatrixCursor(columns)
        val pack = PackManager.getPack(ctx, packId) ?: return cursor
        for (sticker in pack.stickers) {
            cursor.addRow(arrayOf(
                sticker.fileName,
                sticker.emojis.joinToString(",")
            ))
        }
        return cursor
    }
}
