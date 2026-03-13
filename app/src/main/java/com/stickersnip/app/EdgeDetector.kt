package com.stickersnip.app

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect

object EdgeDetector {

    private const val GRAD_MIN = 8f
    private const val DARK_THRESH = 30f
    private const val MIN_SIZE = 50

    fun detectCrop(bitmap: Bitmap): Rect {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        val rowLuma = FloatArray(h)
        for (y in 0 until h) {
            var sum = 0f
            var count = 0
            var x = 0
            while (x < w) {
                val px = pixels[y * w + x]
                sum += luma(px)
                count++
                x += 4
            }
            rowLuma[y] = if (count > 0) sum / count else 0f
        }

        val colLuma = FloatArray(w)
        for (x in 0 until w) {
            var sum = 0f
            var count = 0
            var y = 0
            while (y < h) {
                val px = pixels[y * w + x]
                sum += luma(px)
                count++
                y += 4
            }
            colLuma[x] = if (count > 0) sum / count else 0f
        }

        val smoothRow = smooth(rowLuma, 5)
        val smoothCol = smooth(colLuma, 5)

        var topEdge = findTopEdge(smoothRow, h)
        var bottomEdge = findBottomEdge(smoothRow, h)
        var leftEdge = findLeftEdge(smoothCol, w)
        var rightEdge = findRightEdge(smoothCol, w)

        val pad = 4
        topEdge = (topEdge - pad).coerceIn(0, h - 1)
        bottomEdge = (bottomEdge + pad).coerceIn(0, h - 1)
        leftEdge = (leftEdge - pad).coerceIn(0, w - 1)
        rightEdge = (rightEdge + pad).coerceIn(0, w - 1)

        val resultW = rightEdge - leftEdge
        val resultH = bottomEdge - topEdge
        return if (resultW < MIN_SIZE || resultH < MIN_SIZE) {
            Rect(0, 0, w, h)
        } else {
            Rect(leftEdge, topEdge, rightEdge, bottomEdge)
        }
    }

    fun cropBitmap(original: Bitmap, rect: Rect): Bitmap {
        val cropped = Bitmap.createBitmap(
            original,
            rect.left.coerceIn(0, original.width - 1),
            rect.top.coerceIn(0, original.height - 1),
            (rect.width()).coerceIn(1, original.width - rect.left.coerceIn(0, original.width - 1)),
            (rect.height()).coerceIn(1, original.height - rect.top.coerceIn(0, original.height - 1))
        )
        return Bitmap.createScaledBitmap(cropped, 512, 512, true)
    }

    private fun luma(pixel: Int): Float {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        return 0.299f * r + 0.587f * g + 0.114f * b
    }

    private fun smooth(arr: FloatArray, win: Int): FloatArray {
        val result = FloatArray(arr.size)
        for (i in arr.indices) {
            val from = (i - win).coerceAtLeast(0)
            val to = (i + win).coerceAtMost(arr.size - 1)
            var sum = 0f
            for (j in from..to) sum += arr[j]
            result[i] = sum / (to - from + 1)
        }
        return result
    }

    private fun findTopEdge(smoothRow: FloatArray, h: Int): Int {
        var maxGrad = 0f
        var topEdge = 0
        val limit = (h * 0.6).toInt()
        for (y in 1..limit.coerceAtMost(h - 1)) {
            val grad = smoothRow[y] - smoothRow[y - 1]
            if (grad > maxGrad) {
                maxGrad = grad
                topEdge = y
            }
        }
        if (maxGrad < GRAD_MIN) {
            topEdge = 0
            for (y in 0 until h) {
                if (smoothRow[y] >= DARK_THRESH) {
                    topEdge = y
                    break
                }
            }
        }
        return topEdge
    }

    private fun findBottomEdge(smoothRow: FloatArray, h: Int): Int {
        var maxGrad = 0f
        var bottomEdge = h - 1
        val limit = (h * 0.4).toInt()
        for (y in (h - 2) downTo limit.coerceAtLeast(0)) {
            val grad = smoothRow[y] - smoothRow[y + 1]
            if (grad > maxGrad) {
                maxGrad = grad
                bottomEdge = y
            }
        }
        if (maxGrad < GRAD_MIN) {
            bottomEdge = h - 1
            for (y in (h - 1) downTo 0) {
                if (smoothRow[y] >= DARK_THRESH) {
                    bottomEdge = y
                    break
                }
            }
        }
        return bottomEdge
    }

    private fun findLeftEdge(smoothCol: FloatArray, w: Int): Int {
        var maxGrad = 0f
        var leftEdge = 0
        val limit = (w * 0.6).toInt()
        for (x in 1..limit.coerceAtMost(w - 1)) {
            val grad = smoothCol[x] - smoothCol[x - 1]
            if (grad > maxGrad) {
                maxGrad = grad
                leftEdge = x
            }
        }
        if (maxGrad < GRAD_MIN) {
            leftEdge = 0
            for (x in 0 until w) {
                if (smoothCol[x] >= DARK_THRESH) {
                    leftEdge = x
                    break
                }
            }
        }
        return leftEdge
    }

    private fun findRightEdge(smoothCol: FloatArray, w: Int): Int {
        var maxGrad = 0f
        var rightEdge = w - 1
        val limit = (w * 0.4).toInt()
        for (x in (w - 2) downTo limit.coerceAtLeast(0)) {
            val grad = smoothCol[x] - smoothCol[x + 1]
            if (grad > maxGrad) {
                maxGrad = grad
                rightEdge = x
            }
        }
        if (maxGrad < GRAD_MIN) {
            rightEdge = w - 1
            for (x in (w - 1) downTo 0) {
                if (smoothCol[x] >= DARK_THRESH) {
                    rightEdge = x
                    break
                }
            }
        }
        return rightEdge
    }
}
