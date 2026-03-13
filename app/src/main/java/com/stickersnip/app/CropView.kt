package com.stickersnip.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

class CropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private enum class DragMode {
        NONE, MOVE, TL, TR, BL, BR, TM, BM, ML, MR
    }

    var bitmap: Bitmap? = null
        set(value) {
            field = value
            if (value != null) initCrop()
            invalidate()
        }

    var cropRect: RectF = RectF()
        set(value) {
            field.set(value)
            invalidate()
        }

    var onCropChanged: ((RectF) -> Unit)? = null

    private val imgToView = Matrix()
    private val viewToImg = Matrix()

    private val paintDim = Paint().apply {
        color = 0xAA000000.toInt()
        style = Paint.Style.FILL
    }

    private val paintBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFE135.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f * resources.displayMetrics.density
    }

    private val paintHandle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFE135.toInt()
        style = Paint.Style.FILL
    }

    private val paintGrid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33FFE135
        style = Paint.Style.STROKE
        strokeWidth = 1f * resources.displayMetrics.density
    }

    private val handleRadius = 28f
    private val minCropSize = 80f

    private var dragMode = DragMode.NONE
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    private fun initCrop() {
        val bmp = bitmap ?: return
        val rect = EdgeDetector.detectCrop(bmp)
        cropRect = RectF(rect.left.toFloat(), rect.top.toFloat(), rect.right.toFloat(), rect.bottom.toFloat())
        if (width > 0 && height > 0) computeMatrices()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeMatrices()
    }

    private fun computeMatrices() {
        val bmp = bitmap ?: return
        val vw = width.toFloat()
        val vh = height.toFloat()
        val bw = bmp.width.toFloat()
        val bh = bmp.height.toFloat()

        val scale = min(vw / bw, vh / bh)
        val dx = (vw - bw * scale) / 2f
        val dy = (vh - bh * scale) / 2f

        imgToView.reset()
        imgToView.setScale(scale, scale)
        imgToView.postTranslate(dx, dy)

        imgToView.invert(viewToImg)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = bitmap ?: return

        // Draw bitmap
        canvas.drawBitmap(bmp, imgToView, null)

        // Map crop rect to view coordinates
        val viewCrop = RectF()
        imgToView.mapRect(viewCrop, cropRect)

        // Draw dim regions outside crop
        canvas.drawRect(0f, 0f, width.toFloat(), viewCrop.top, paintDim)
        canvas.drawRect(0f, viewCrop.bottom, width.toFloat(), height.toFloat(), paintDim)
        canvas.drawRect(0f, viewCrop.top, viewCrop.left, viewCrop.bottom, paintDim)
        canvas.drawRect(viewCrop.right, viewCrop.top, width.toFloat(), viewCrop.bottom, paintDim)

        // Draw border
        canvas.drawRect(viewCrop, paintBorder)

        // Draw rule-of-thirds grid lines
        val thirdW = viewCrop.width() / 3f
        val thirdH = viewCrop.height() / 3f
        canvas.drawLine(viewCrop.left + thirdW, viewCrop.top, viewCrop.left + thirdW, viewCrop.bottom, paintGrid)
        canvas.drawLine(viewCrop.left + 2 * thirdW, viewCrop.top, viewCrop.left + 2 * thirdW, viewCrop.bottom, paintGrid)
        canvas.drawLine(viewCrop.left, viewCrop.top + thirdH, viewCrop.right, viewCrop.top + thirdH, paintGrid)
        canvas.drawLine(viewCrop.left, viewCrop.top + 2 * thirdH, viewCrop.right, viewCrop.top + 2 * thirdH, paintGrid)

        // Draw 8 handles
        val handles = getHandlePositions(viewCrop)
        for (pos in handles) {
            canvas.drawCircle(pos[0], pos[1], handleRadius, paintHandle)
        }
    }

    private fun getHandlePositions(r: RectF): Array<FloatArray> {
        val cx = (r.left + r.right) / 2f
        val cy = (r.top + r.bottom) / 2f
        return arrayOf(
            floatArrayOf(r.left, r.top),       // TL
            floatArrayOf(r.right, r.top),       // TR
            floatArrayOf(r.left, r.bottom),     // BL
            floatArrayOf(r.right, r.bottom),    // BR
            floatArrayOf(cx, r.top),            // TM
            floatArrayOf(cx, r.bottom),         // BM
            floatArrayOf(r.left, cy),           // ML
            floatArrayOf(r.right, cy)           // MR
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val viewCrop = RectF()
                imgToView.mapRect(viewCrop, cropRect)
                dragMode = hitTest(x, y, viewCrop)
                if (dragMode != DragMode.NONE) {
                    lastTouchX = x
                    lastTouchY = y
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragMode == DragMode.NONE) return false
                val dx = x - lastTouchX
                val dy = y - lastTouchY

                val viewCrop = RectF()
                imgToView.mapRect(viewCrop, cropRect)

                applyDrag(viewCrop, dx, dy)

                // Convert back to image coords
                val imgRect = RectF()
                viewToImg.mapRect(imgRect, viewCrop)

                // Clamp to bitmap bounds
                val bmp = bitmap ?: return true
                imgRect.left = imgRect.left.coerceIn(0f, bmp.width.toFloat())
                imgRect.top = imgRect.top.coerceIn(0f, bmp.height.toFloat())
                imgRect.right = imgRect.right.coerceIn(0f, bmp.width.toFloat())
                imgRect.bottom = imgRect.bottom.coerceIn(0f, bmp.height.toFloat())

                cropRect.set(imgRect)
                onCropChanged?.invoke(cropRect)
                invalidate()

                lastTouchX = x
                lastTouchY = y
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragMode = DragMode.NONE
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun hitTest(x: Float, y: Float, viewCrop: RectF): DragMode {
        val handles = getHandlePositions(viewCrop)
        val modes = arrayOf(
            DragMode.TL, DragMode.TR, DragMode.BL, DragMode.BR,
            DragMode.TM, DragMode.BM, DragMode.ML, DragMode.MR
        )
        val threshold = handleRadius * 2
        val thresholdSq = threshold * threshold
        for (i in handles.indices) {
            val hx = handles[i][0]
            val hy = handles[i][1]
            val distSq = (x - hx) * (x - hx) + (y - hy) * (y - hy)
            if (distSq <= thresholdSq) return modes[i]
        }
        if (viewCrop.contains(x, y)) return DragMode.MOVE
        return DragMode.NONE
    }

    private fun applyDrag(viewCrop: RectF, dx: Float, dy: Float) {
        // Get bitmap view bounds for clamping
        val bmp = bitmap ?: return
        val bmpViewRect = RectF(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat())
        imgToView.mapRect(bmpViewRect)

        when (dragMode) {
            DragMode.MOVE -> {
                var offsetX = dx
                var offsetY = dy
                if (viewCrop.left + offsetX < bmpViewRect.left) offsetX = bmpViewRect.left - viewCrop.left
                if (viewCrop.right + offsetX > bmpViewRect.right) offsetX = bmpViewRect.right - viewCrop.right
                if (viewCrop.top + offsetY < bmpViewRect.top) offsetY = bmpViewRect.top - viewCrop.top
                if (viewCrop.bottom + offsetY > bmpViewRect.bottom) offsetY = bmpViewRect.bottom - viewCrop.bottom
                viewCrop.offset(offsetX, offsetY)
            }
            DragMode.TL -> {
                val newLeft = (viewCrop.left + dx).coerceIn(bmpViewRect.left, viewCrop.right - minCropSize)
                val newTop = (viewCrop.top + dy).coerceIn(bmpViewRect.top, viewCrop.bottom - minCropSize)
                viewCrop.left = newLeft
                viewCrop.top = newTop
            }
            DragMode.TR -> {
                val newRight = (viewCrop.right + dx).coerceIn(viewCrop.left + minCropSize, bmpViewRect.right)
                val newTop = (viewCrop.top + dy).coerceIn(bmpViewRect.top, viewCrop.bottom - minCropSize)
                viewCrop.right = newRight
                viewCrop.top = newTop
            }
            DragMode.BL -> {
                val newLeft = (viewCrop.left + dx).coerceIn(bmpViewRect.left, viewCrop.right - minCropSize)
                val newBottom = (viewCrop.bottom + dy).coerceIn(viewCrop.top + minCropSize, bmpViewRect.bottom)
                viewCrop.left = newLeft
                viewCrop.bottom = newBottom
            }
            DragMode.BR -> {
                val newRight = (viewCrop.right + dx).coerceIn(viewCrop.left + minCropSize, bmpViewRect.right)
                val newBottom = (viewCrop.bottom + dy).coerceIn(viewCrop.top + minCropSize, bmpViewRect.bottom)
                viewCrop.right = newRight
                viewCrop.bottom = newBottom
            }
            DragMode.TM -> {
                val newTop = (viewCrop.top + dy).coerceIn(bmpViewRect.top, viewCrop.bottom - minCropSize)
                viewCrop.top = newTop
            }
            DragMode.BM -> {
                val newBottom = (viewCrop.bottom + dy).coerceIn(viewCrop.top + minCropSize, bmpViewRect.bottom)
                viewCrop.bottom = newBottom
            }
            DragMode.ML -> {
                val newLeft = (viewCrop.left + dx).coerceIn(bmpViewRect.left, viewCrop.right - minCropSize)
                viewCrop.left = newLeft
            }
            DragMode.MR -> {
                val newRight = (viewCrop.right + dx).coerceIn(viewCrop.left + minCropSize, bmpViewRect.right)
                viewCrop.right = newRight
            }
            DragMode.NONE -> { /* no-op */ }
        }
    }

    fun getCroppedBitmap(): Bitmap? {
        val bmp = bitmap ?: return null
        val left = cropRect.left.toInt().coerceIn(0, bmp.width - 1)
        val top = cropRect.top.toInt().coerceIn(0, bmp.height - 1)
        var w = cropRect.width().toInt().coerceIn(1, bmp.width - left)
        var h = cropRect.height().toInt().coerceIn(1, bmp.height - top)
        if (left + w > bmp.width) w = bmp.width - left
        if (top + h > bmp.height) h = bmp.height - top
        val cropped = Bitmap.createBitmap(bmp, left, top, w, h)
        return Bitmap.createScaledBitmap(cropped, 512, 512, true)
    }
}
