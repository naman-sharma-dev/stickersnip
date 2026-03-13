package com.stickersnip.app

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.stickersnip.app.databinding.ActivityMainBinding
import com.stickersnip.app.databinding.ItemPackBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val packs = mutableListOf<StickerPack>()
    private lateinit var adapter: PackAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = PackAdapter()
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        binding.fabNew.setOnClickListener {
            val intent = Intent(this, CreatePackActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPacks()
    }

    private fun refreshPacks() {
        packs.clear()
        packs.addAll(PackManager.loadAllPacks(this))
        adapter.notifyDataSetChanged()
        updateEmptyState()
    }

    private fun updateEmptyState() {
        if (packs.isEmpty()) {
            binding.emptyView.visibility = View.VISIBLE
            binding.recycler.visibility = View.GONE
        } else {
            binding.emptyView.visibility = View.GONE
            binding.recycler.visibility = View.VISIBLE
        }
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == WhatsAppHelper.REQUEST_CODE_ADD && resultCode == RESULT_OK) {
            Toast.makeText(this, "Added to WhatsApp!", Toast.LENGTH_SHORT).show()
        }
    }

    inner class PackAdapter : RecyclerView.Adapter<PackAdapter.PackViewHolder>() {

        inner class PackViewHolder(val itemBinding: ItemPackBinding) :
            RecyclerView.ViewHolder(itemBinding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PackViewHolder {
            val b = ItemPackBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return PackViewHolder(b)
        }

        override fun onBindViewHolder(holder: PackViewHolder, position: Int) {
            val pack = packs[position]
            val b = holder.itemBinding

            b.tvPackName.text = pack.name
            b.tvStickerCount.text = "${pack.stickers.size} sticker${if (pack.stickers.size != 1) "s" else ""}"

            // Load tray icon
            val trayFile = PackManager.getTrayFile(this@MainActivity, pack.id)
            if (trayFile.exists()) {
                val bmp = android.graphics.BitmapFactory.decodeFile(trayFile.absolutePath)
                b.ivTray.setImageBitmap(bmp)
            } else {
                b.ivTray.setImageResource(R.drawable.ic_sticker_placeholder)
            }

            b.btnWhatsapp.setOnClickListener {
                if (pack.stickers.size < 3) {
                    Toast.makeText(this@MainActivity, "Need at least 3 stickers", Toast.LENGTH_SHORT).show()
                } else {
                    WhatsAppHelper.addToWhatsApp(this@MainActivity, pack)
                }
            }

            b.btnEdit.setOnClickListener {
                val intent = Intent(this@MainActivity, CreatePackActivity::class.java)
                intent.putExtra("pack_id", pack.id)
                startActivity(intent)
            }

            b.btnDelete.setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Delete Pack")
                    .setMessage("Delete \"${pack.name}\" and all its stickers?")
                    .setPositiveButton("Delete") { _, _ ->
                        PackManager.deletePack(this@MainActivity, pack.id)
                        refreshPacks()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }

        override fun getItemCount(): Int = packs.size
    }
}
