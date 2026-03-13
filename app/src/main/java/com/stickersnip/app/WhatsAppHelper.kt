package com.stickersnip.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast

object WhatsAppHelper {

    private const val WHATSAPP_CONSUMER_PACKAGE = "com.whatsapp"
    private const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"
    private const val ADD_STICKER_PACK_ACTION = "com.whatsapp.intent.action.ENABLE_STICKER_PACK"
    private const val STICKER_PACK_ID_EXTRA = "sticker_pack_id"
    private const val STICKER_PACK_AUTHORITY_EXTRA = "sticker_pack_authority"
    private const val STICKER_PACK_NAME_EXTRA = "sticker_pack_name"
    const val REQUEST_CODE_ADD = 200

    fun addToWhatsApp(activity: Activity, pack: StickerPack) {
        val authority = StickerContentProvider.getAuthority(activity)
        val intent = Intent().apply {
            action = ADD_STICKER_PACK_ACTION
            putExtra(STICKER_PACK_ID_EXTRA, pack.id)
            putExtra(STICKER_PACK_AUTHORITY_EXTRA, authority)
            putExtra(STICKER_PACK_NAME_EXTRA, pack.name)
        }
        try {
            @Suppress("DEPRECATION")
            activity.startActivityForResult(intent, REQUEST_CODE_ADD)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(activity, "WhatsApp is not installed", Toast.LENGTH_SHORT).show()
        }
    }
}
