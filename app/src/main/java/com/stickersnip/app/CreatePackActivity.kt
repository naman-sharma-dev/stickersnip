package com.stickersnip.app

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.stickersnip.app.databinding.ActivityCreatePackBinding
import com.stickersnip.app.databinding.ItemSavedStickerBinding
import java.io.InputStream

class CreatePackActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreatePackBinding
    private var pack: StickerPack? = null
    private val cropViews = mutableListOf<CropView>()
    private lateinit var savedAdapter: SavedStickerAdapter

    companion object {
        private const val PICK_IMAGES_REQUEST = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreatePackBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val packId = intent.getStringExtra("pack_id")
        if (packId != null) {
            pack = PackManager.getPack(this, packId)
        }
        if (pack == null) {
            pack = PackManager.createNewPack(this, "My Sticker Pack")
        }

        binding.etPackName.setText(pack!!.name)

        savedAdapter = SavedStickerAdapter()
        binding.stickerGrid.layoutManager = GridLayoutManager(this, 4)
        binding.stickerGrid.adapter = savedAdapter

        updateWhatsAppButtonState()

        binding.btnPickImages.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            @Suppress("DEPRECATION")
            startActivityForResult(Intent.createChooser(intent, "Pick Screenshots"), PICK_IMAGES_REQUEST)
        }

        binding.btnAutoCropAll.setOnClickListener {
            for (cv in cropViews) {
                val bmp = cv.bitmap ?: continue
                val rect = EdgeDetector.detectCrop(bmp)
                cv.cropRect = RectF(rect.left.toFloat(), rect.top.toFloat(), rect.right.toFloat(), rect.bottom.toFloat())
            }
        }

        binding.btnSavePack.setOnClickListener {
            val name = binding.etPackName.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "Enter a pack name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            pack!!.name = name
            PackManager.savePack(this, pack!!)
            Toast.makeText(this, "Pack saved!", Toast.LENGTH_SHORT).show()
            finish()
        }

        binding.btnAddToWhatsapp.setOnClickListener {
            val currentPack = pack ?: return@setOnClickListener
            if (currentPack.stickers.size < 3) {
                Toast.makeText(this, "Need at least 3 stickers", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val name = binding.etPackName.text.toString().trim()
            if (name.isNotEmpty()) {
                currentPack.name = name
                PackManager.savePack(this, currentPack)
            }
            WhatsAppHelper.addToWhatsApp(this, currentPack)
        }
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGES_REQUEST && resultCode == RESULT_OK && data != null) {
            binding.progressBar.visibility = View.VISIBLE
            binding.tvStatus.text = "Loading images..."

            val uris = mutableListOf<Uri>()
            val clipData = data.clipData
            if (clipData != null) {
                for (i in 0 until clipData.itemCount) {
                    uris.add(clipData.getItemAt(i).uri)
                }
            } else {
                data.data?.let { uris.add(it) }
            }

            Thread {
                val bitmaps = uris.mapNotNull { uri -> decodeSampledBitmap(uri) }
                runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                    binding.tvStatus.text = "${bitmaps.size} image(s) loaded"
                    for (bmp in bitmaps) {
                        addCropCard(bmp)
                    }
                    if (bitmaps.isNotEmpty()) {
                        binding.btnAutoCropAll.visibility = View.VISIBLE
                    }
                }
            }.start()
        } else if (requestCode == WhatsAppHelper.REQUEST_CODE_ADD && resultCode == RESULT_OK) {
            Toast.makeText(this, "Added to WhatsApp!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun decodeSampledBitmap(uri: Uri): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            var stream: InputStream? = contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(stream, null, options)
            stream?.close()

            var sampleSize = 1
            val maxDim = 1080
            while ((options.outWidth / sampleSize) > maxDim || (options.outHeight / sampleSize) > maxDim) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            stream = contentResolver.openInputStream(uri)
            val bmp = BitmapFactory.decodeStream(stream, null, decodeOptions)
            stream?.close()
            bmp
        } catch (e: Exception) {
            null
        }
    }

    private fun addCropCard(bitmap: Bitmap) {
        val cardView = LayoutInflater.from(this).inflate(R.layout.item_crop_card, binding.cropContainer, false)
        val cropView = cardView.findViewById<CropView>(R.id.cropView)
        val btnAddToPack = cardView.findViewById<View>(R.id.btnAddToPack)

        cropView.bitmap = bitmap
        cropViews.add(cropView)

        btnAddToPack.setOnClickListener {
            val cropped = cropView.getCroppedBitmap()
            if (cropped != null) {
                val currentPack = pack ?: return@setOnClickListener

                // Save tray icon from first sticker
                if (currentPack.stickers.isEmpty()) {
                    PackManager.saveTrayIcon(this, currentPack.id, cropped)
                }

                PackManager.addStickerToPack(this, currentPack.id, cropped)
                pack = PackManager.getPack(this, currentPack.id)

                // Remove card
                binding.cropContainer.removeView(cardView)
                cropViews.remove(cropView)

                if (cropViews.isEmpty()) {
                    binding.btnAutoCropAll.visibility = View.GONE
                }

                savedAdapter.notifyDataSetChanged()
                updateWhatsAppButtonState()
                binding.tvStatus.text = "${pack!!.stickers.size} sticker(s) in pack"
                Toast.makeText(this, "Sticker added!", Toast.LENGTH_SHORT).show()
            }
        }

        binding.cropContainer.addView(cardView)
    }

    private fun updateWhatsAppButtonState() {
        val count = pack?.stickers?.size ?: 0
        binding.btnAddToWhatsapp.isEnabled = count >= 3
        binding.btnAddToWhatsapp.alpha = if (count >= 3) 1.0f else 0.4f
    }

    inner class SavedStickerAdapter : RecyclerView.Adapter<SavedStickerAdapter.StickerViewHolder>() {

        inner class StickerViewHolder(val itemBinding: ItemSavedStickerBinding) :
            RecyclerView.ViewHolder(itemBinding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StickerViewHolder {
            val b = ItemSavedStickerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return StickerViewHolder(b)
        }

        override fun onBindViewHolder(holder: StickerViewHolder, position: Int) {
            val currentPack = pack ?: return
            val sticker = currentPack.stickers[position]

            val bmp = PackManager.loadStickerBitmap(this@CreatePackActivity, currentPack.id, sticker.fileName)
            if (bmp != null) {
                holder.itemBinding.ivSticker.setImageBitmap(bmp)
                holder.itemBinding.ivSticker.scaleType = ImageView.ScaleType.CENTER_CROP
            } else {
                holder.itemBinding.ivSticker.setImageResource(R.drawable.ic_sticker_placeholder)
            }

            holder.itemView.setOnLongClickListener {
                AlertDialog.Builder(this@CreatePackActivity)
                    .setTitle("Remove Sticker")
                    .setMessage("Remove this sticker from the pack?")
                    .setPositiveButton("Remove") { _, _ ->
                        PackManager.removeStickerFromPack(
                            this@CreatePackActivity,
                            currentPack.id,
                            position
                        )
                        pack = PackManager.getPack(this@CreatePackActivity, currentPack.id)
                        notifyDataSetChanged()
                        updateWhatsAppButtonState()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                true
            }
        }

        override fun getItemCount(): Int = pack?.stickers?.size ?: 0
    }
}
