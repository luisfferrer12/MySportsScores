package com.fermundev.mysportsscores.ui.gallery

import android.app.AlertDialog
import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.fermundev.mysportsscores.R
import com.fermundev.mysportsscores.databinding.FragmentGalleryBinding
import com.fermundev.mysportsscores.databinding.ItemGalleryPhotoBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class GalleryFragment : Fragment() {

    private var _binding: FragmentGalleryBinding? = null
    private val binding get() = _binding!!

    private val user = FirebaseAuth.getInstance().currentUser
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private lateinit var galleryAdapter: GalleryAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGalleryBinding.inflate(inflater, container, false)
        setupRecyclerView()
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        loadGalleryImages()
    }

    fun refreshGallery() {
        loadGalleryImages()
    }

    private fun setupRecyclerView() {
        galleryAdapter = GalleryAdapter(emptyList()) { uri ->
            showFullScreenImage(uri)
        }
        binding.galleryRecyclerview.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter = galleryAdapter
        }
    }

    private fun loadGalleryImages() {
        if (user?.email == null) {
            showEmptyState("Usuario no identificado.")
            return
        }

        binding.galleryProgressbar.visibility = View.VISIBLE
        binding.galleryRecyclerview.visibility = View.GONE
        binding.emptyGalleryMessage.visibility = View.GONE

        // 1. Get active sport first
        db.collection("users").document(user.email!!).get()
            .addOnSuccessListener { userDoc ->
                if (_binding == null) return@addOnSuccessListener
                val activeSport = userDoc.getString("grupoActivo")

                if (activeSport.isNullOrEmpty()) {
                    showEmptyState("Selecciona un deporte activo para ver su galería")
                    return@addOnSuccessListener
                }

                // 2. Build dynamic path and list images
                val galleryRef = storage.reference.child("MySportsScores/${user.email}/gallery/$activeSport")
                galleryRef.listAll()
                    .addOnSuccessListener { listResult ->
                        if (_binding == null) return@addOnSuccessListener

                        if (listResult.items.isEmpty()) {
                            showEmptyState("No hay fotos para el deporte '$activeSport'")
                            return@addOnSuccessListener
                        }

                        val photoUris = mutableListOf<Uri>()
                        var itemsProcessed = 0
                        listResult.items.forEach { item ->
                            item.downloadUrl.addOnSuccessListener { uri ->
                                photoUris.add(uri)
                                itemsProcessed++
                                if (itemsProcessed == listResult.items.size) {
                                    showPhotos(photoUris)
                                }
                            }.addOnFailureListener {
                                itemsProcessed++
                                if (itemsProcessed == listResult.items.size) {
                                   if (photoUris.isNotEmpty()) {
                                        showPhotos(photoUris)
                                    } else {
                                        showEmptyState("Error al cargar la galería.")
                                    }
                                }
                            }
                        }
                    }
                    .addOnFailureListener { 
                        if (_binding == null) return@addOnFailureListener
                        showEmptyState("Error al acceder a la galería.") 
                    }
            }
            .addOnFailureListener {
                 if (_binding == null) return@addOnFailureListener
                showEmptyState("Error al obtener datos del usuario.") 
            }
    }

    private fun showFullScreenImage(uri: Uri) {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_full_screen_gallery_image)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        val fullScreenImageView = dialog.findViewById<ImageView>(R.id.full_screen_imageview)
        val deleteButton = dialog.findViewById<ImageButton>(R.id.delete_photo_button)

        Glide.with(this).load(uri).into(fullScreenImageView)

        deleteButton.setOnClickListener {
            showDeleteConfirmationDialog(uri, dialog)
        }

        fullScreenImageView.setOnClickListener { dialog.dismiss() } // Cerrar al tocar la imagen
        dialog.show()
    }

    private fun showDeleteConfirmationDialog(uri: Uri, parentDialog: Dialog) {
        AlertDialog.Builder(requireContext())
            .setTitle("Eliminar Foto")
            .setMessage("¿Seguro que deseas eliminar esta foto?")
            .setPositiveButton("Eliminar") { _, _ ->
                deletePhoto(uri)
                parentDialog.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun deletePhoto(uri: Uri) {
        val photoRef = storage.getReferenceFromUrl(uri.toString())
        photoRef.delete()
            .addOnSuccessListener {
                Toast.makeText(context, "Foto eliminada", Toast.LENGTH_SHORT).show()
                loadGalleryImages() // Recargar la galería
            }
            .addOnFailureListener {
                Toast.makeText(context, "Error al eliminar la foto", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showPhotos(photoUris: List<Uri>){
        if (_binding == null) return
        binding.galleryProgressbar.visibility = View.GONE
        binding.galleryRecyclerview.visibility = View.VISIBLE
        galleryAdapter.updateData(photoUris)
    }

    private fun showEmptyState(message: String) {
        if (_binding == null) return
        binding.galleryProgressbar.visibility = View.GONE
        binding.galleryRecyclerview.visibility = View.GONE
        binding.emptyGalleryMessage.visibility = View.VISIBLE
        binding.emptyGalleryMessage.text = message
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class GalleryAdapter(
    private var photoUris: List<Uri>,
    private val onPhotoClicked: (Uri) -> Unit
) : RecyclerView.Adapter<GalleryAdapter.PhotoViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val binding = ItemGalleryPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PhotoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(photoUris[position])
    }

    override fun getItemCount(): Int = photoUris.size

    fun updateData(newUris: List<Uri>) {
        photoUris = newUris
        notifyDataSetChanged()
    }

    inner class PhotoViewHolder(private val binding: ItemGalleryPhotoBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(uri: Uri) {
            Glide.with(itemView.context)
                .load(uri)
                .centerCrop()
                .into(binding.galleryImageview)
            
            itemView.setOnClickListener { onPhotoClicked(uri) }
        }
    }
}