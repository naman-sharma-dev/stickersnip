package com.stickersnip.app

import org.json.JSONObject

data class Sticker(
    val fileName: String,
    val emojis: List<String> = listOf("😀")
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("fileName", fileName)
        put("emojis", org.json.JSONArray(emojis))
    }

    companion object {
        fun fromJson(json: JSONObject): Sticker {
            val emojis = mutableListOf<String>()
            val arr = json.optJSONArray("emojis")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    emojis.add(arr.getString(i))
                }
            }
            if (emojis.isEmpty()) emojis.add("😀")
            return Sticker(
                fileName = json.getString("fileName"),
                emojis = emojis
            )
        }
    }
}

data class StickerPack(
    val id: String,
    var name: String,
    val publisher: String = "StickerSnip",
    val trayImageFile: String = "tray.png",
    val stickers: MutableList<Sticker> = mutableListOf()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("publisher", publisher)
        put("trayImageFile", trayImageFile)
        val arr = org.json.JSONArray()
        stickers.forEach { arr.put(it.toJson()) }
        put("stickers", arr)
    }

    companion object {
        fun fromJson(json: JSONObject): StickerPack {
            val stickers = mutableListOf<Sticker>()
            val arr = json.optJSONArray("stickers")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    stickers.add(Sticker.fromJson(arr.getJSONObject(i)))
                }
            }
            return StickerPack(
                id = json.getString("id"),
                name = json.getString("name"),
                publisher = json.optString("publisher", "StickerSnip"),
                trayImageFile = json.optString("trayImageFile", "tray.png"),
                stickers = stickers
            )
        }
    }
}
