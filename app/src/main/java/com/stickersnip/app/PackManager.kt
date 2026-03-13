package com.stickersnip.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object PackManager {

    private const val META_FILE = "packs_meta.json"
    private const val PACKS_DIR = "sticker_packs"

    fun loadAllPacks(ctx: Context): List<StickerPack> {
        val file = File(ctx.filesDir, META_FILE)
        if (!file.exists()) return emptyList()
        return try {
            val json = file.readText()
            val arr = JSONArray(json)
            val packs = mutableListOf<StickerPack>()
            for (i in 0 until arr.length()) {
                packs.add(StickerPack.fromJson(arr.getJSONObject(i)))
            }
            packs
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun savePack(ctx: Context, pack: StickerPack) {
        val packs = loadAllPacks(ctx).toMutableList()
        val index = packs.indexOfFirst { it.id == pack.id }
        if (index >= 0) {
            packs[index] = pack
        } else {
            packs.add(pack)
        }
        saveAllPacks(ctx, packs)
    }

    fun deletePack(ctx: Context, packId: String) {
        val packs = loadAllPacks(ctx).toMutableList()
        packs.removeAll { it.id == packId }
        saveAllPacks(ctx, packs)
        val packDir = getPackDir(ctx, packId)
        if (packDir.exists()) packDir.deleteRecursively()
    }

    fun getPack(ctx: Context, packId: String): StickerPack? {
        return loadAllPacks(ctx).find { it.id == packId }
    }

    fun createNewPack(ctx: Context, name: String): StickerPack {
        val id = UUID.randomUUID().toString().replace("-", "").take(16)
        val pack = StickerPack(id = id, name = name)
        getPackDir(ctx, id).mkdirs()
        savePack(ctx, pack)
        return pack
    }

    fun addStickerToPack(ctx: Context, packId: String, bitmap: Bitmap): Sticker? {
        val pack = getPack(ctx, packId) ?: return null
        val packDir = getPackDir(ctx, packId)
        packDir.mkdirs()

        val index = pack.stickers.size + 1
        val fileName = "sticker_%03d.webp".format(index)
        val file = File(packDir, fileName)

        val scaled = if (bitmap.width != 512 || bitmap.height != 512) {
            Bitmap.createScaledBitmap(bitmap, 512, 512, true)
        } else {
            bitmap
        }

        FileOutputStream(file).use { fos ->
            scaled.compress(Bitmap.CompressFormat.WEBP, 90, fos)
        }

        val sticker = Sticker(fileName = fileName)
        pack.stickers.add(sticker)
        savePack(ctx, pack)
        return sticker
    }

    fun removeStickerFromPack(ctx: Context, packId: String, stickerIndex: Int) {
        val pack = getPack(ctx, packId) ?: return
        if (stickerIndex < 0 || stickerIndex >= pack.stickers.size) return
        val sticker = pack.stickers.removeAt(stickerIndex)
        val file = File(getPackDir(ctx, packId), sticker.fileName)
        if (file.exists()) file.delete()
        savePack(ctx, pack)
    }

    fun saveTrayIcon(ctx: Context, packId: String, bitmap: Bitmap) {
        val packDir = getPackDir(ctx, packId)
        packDir.mkdirs()
        val tray = Bitmap.createScaledBitmap(bitmap, 96, 96, true)
        val file = File(packDir, "tray.png")
        FileOutputStream(file).use { fos ->
            tray.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
    }

    fun getStickerFile(ctx: Context, packId: String, fileName: String): File {
        return File(getPackDir(ctx, packId), fileName)
    }

    fun getTrayFile(ctx: Context, packId: String): File {
        return File(getPackDir(ctx, packId), "tray.png")
    }

    fun loadStickerBitmap(ctx: Context, packId: String, fileName: String): Bitmap? {
        val file = getStickerFile(ctx, packId, fileName)
        if (!file.exists()) return null
        return BitmapFactory.decodeFile(file.absolutePath)
    }

    fun getPackDir(ctx: Context, packId: String): File {
        return File(ctx.filesDir, "$PACKS_DIR/$packId")
    }

    private fun saveAllPacks(ctx: Context, packs: List<StickerPack>) {
        val arr = JSONArray()
        packs.forEach { arr.put(it.toJson()) }
        File(ctx.filesDir, META_FILE).writeText(arr.toString())
    }
}
