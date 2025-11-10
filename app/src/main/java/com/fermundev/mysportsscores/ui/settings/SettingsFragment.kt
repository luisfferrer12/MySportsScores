package com.fermundev.mysportsscores.ui.settings

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.fermundev.mysportsscores.R
import com.fermundev.mysportsscores.databinding.FragmentSettingsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()
    private val user = FirebaseAuth.getInstance().currentUser
    private val storage = FirebaseStorage.getInstance()
    private var provider: String? = null

    // Variables para guardar los cambios pendientes
    private var newUsername = ""
    private var newPassword = ""
    private var newProfileImageUri: Uri? = null
    private var notificationsStatus = true

    private var loadingDialog: AlertDialog? = null

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            binding.profileImageView.setImageURI(it)
            newProfileImageUri = it
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        val prefs = requireActivity().getSharedPreferences(getString(R.string.prefs_file), Context.MODE_PRIVATE)
        provider = prefs.getString("provider", null)

        rechargeUserInfo()
        setupNotificationsSwitch()
        setupProfileImageListeners()
        setupAccountSection()
        setupActionButtons()

        return binding.root
    }

    private fun rechargeUserInfo() {
        showLoadingDialog(true)
        user?.email?.let {
            db.collection("users").document(it).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val nickName = document.getString("nickName") ?: ""
                        binding.welcomeTextView.text = "Hola, $nickName"

                        notificationsStatus = document.getBoolean("notificaciones") ?: true
                        binding.notificationsSwitch.isChecked = notificationsStatus

                        val imageUrl = document.getString("profileImageUrl")
                        if (!imageUrl.isNullOrEmpty()) {
                            Glide.with(this).load(imageUrl).placeholder(R.drawable.avatar_1).error(R.drawable.avatar_1).circleCrop().into(binding.profileImageView)
                        }

                        val activeGroup = document.getString("grupoActivo")
                        binding.activeSportTextView.text = if (activeGroup.isNullOrEmpty()) "Ninguno" else activeGroup
                    }
                    showLoadingDialog(false)
                }
                .addOnFailureListener {
                    showLoadingDialog(false)
                    Toast.makeText(requireContext(), "Error al cargar los datos", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun saveUserData() {
        showLoadingDialog(true)
        val updates = mutableMapOf<String, Any>()
        if (newUsername.isNotEmpty()) { updates["nickName"] = newUsername }
        updates["notificaciones"] = notificationsStatus

        val topic = "MySportsNotifications"
        if (notificationsStatus) {
            FirebaseMessaging.getInstance().subscribeToTopic(topic)
        } else {
            FirebaseMessaging.getInstance().unsubscribeFromTopic(topic)
        }

        fun updateFirestore(imageUrl: String? = null) {
            imageUrl?.let { updates["profileImageUrl"] = it }
            user?.email?.let { email ->
                if (updates.isNotEmpty()) {
                    db.collection("users").document(email).update(updates)
                        .addOnSuccessListener {
                            showLoadingDialog(false)
                            Toast.makeText(requireContext(), "Datos guardados con éxito", Toast.LENGTH_SHORT).show()
                            findNavController().navigateUp()
                        }
                        .addOnFailureListener { e ->
                            showLoadingDialog(false)
                            Toast.makeText(requireContext(), "Error al guardar los datos: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    showLoadingDialog(false)
                    findNavController().navigateUp()
                }
            }
        }

        if (newPassword.isNotEmpty() && provider == "BASIC") {
            user?.updatePassword(newPassword)?.addOnCompleteListener { /* ... */ }
        }

        if (newProfileImageUri != null) {
            val profileImagesRef = storage.reference.child("MySportsScores/profile_images/${user?.email}/${user?.uid}.jpg")
            profileImagesRef.putFile(newProfileImageUri!!)
                .addOnSuccessListener { 
                    profileImagesRef.downloadUrl.addOnSuccessListener { downloadUri ->
                        updateFirestore(downloadUri.toString())
                    }
                }
                .addOnFailureListener { e ->
                    showLoadingDialog(false)
                    Toast.makeText(requireContext(), "Error al subir la imagen: ${e.message}", Toast.LENGTH_LONG).show()
                }
        } else {
            updateFirestore()
        }
    }

    // ... (el resto del código permanece igual)
    private fun showLoadingDialog(show: Boolean) {
        if (show) {
            if (loadingDialog == null) {
                val builder = AlertDialog.Builder(requireContext())
                val progressBar = ProgressBar(requireContext())
                builder.setView(progressBar)
                builder.setCancelable(false)
                loadingDialog = builder.create()
                loadingDialog?.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            }
            loadingDialog?.show()
        } else {
            loadingDialog?.dismiss()
        }
    }
    private fun setupProfileImageListeners() {
        binding.editProfileIcon.setOnClickListener { galleryLauncher.launch("image/*") }
        binding.profileImageView.setOnClickListener { showImageFullScreen() }
    }
    private fun setupNotificationsSwitch() {
        binding.notificationsSwitch.setOnCheckedChangeListener { _, isChecked ->
            notificationsStatus = isChecked
        }
    }

    private fun setupAccountSection() {
        binding.editUsernameButton.setOnClickListener { showEditUsernameDialog() }
        binding.changePasswordButton.setOnClickListener {
            if (provider == "GOOGLE") {
                showGooglePasswordResetDialog()
            } else {
                showChangePasswordDialog()
            }
        }
    }

    private fun setupActionButtons() {
        binding.cancelButton.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Cancelar Ajustes")
                .setMessage("¿Seguro que deseas Cancelar los Ajustes?")
                .setPositiveButton("Borrar") { _, _ -> findNavController().navigateUp() }
                .setNegativeButton("Seguir Editando", null)
                .show()
        }

        binding.saveButton.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Guardar Ajustes")
                .setMessage("¿Seguro que deseas Guardar los nuevos ajustes?")
                .setPositiveButton("Guardar") { _, _ -> saveUserData() }
                .setNegativeButton("Seguir Editando", null)
                .show()
        }
    }

    private fun showGooglePasswordResetDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Cambio de Contraseña (Google)")
            .setMessage("Estás autenticado con Google. Para cambiar tu contraseña, te enviaremos un correo de restablecimiento.")
            .setPositiveButton("Enviar Correo") { _, _ ->
                user?.email?.let { email ->
                    FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                        .addOnSuccessListener { Toast.makeText(requireContext(), "Correo de restablecimiento enviado a $email", Toast.LENGTH_LONG).show() }
                        .addOnFailureListener { e -> Toast.makeText(requireContext(), "Error al enviar el correo: ${e.message}", Toast.LENGTH_LONG).show() }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showEditUsernameDialog() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Editar nombre de usuario")

        val container = FrameLayout(requireContext())
        val input = EditText(requireContext())
        input.hint = binding.welcomeTextView.text.toString().substringAfter("Hola, ")
        container.addView(input)

        val padding = (20 * resources.displayMetrics.density).toInt()
        container.setPadding(padding, 0, padding, 0)
        builder.setView(container)

        builder.setPositiveButton("Guardar") { _, _ ->
            val newUsernameWrited = input.text.toString().trim()
            if (newUsernameWrited.isNotEmpty()) {
                binding.editUsernameButton.text = newUsernameWrited
                newUsername = newUsernameWrited
            }
        }
        builder.setNegativeButton("Cancelar", null)
        builder.show()
    }

    private fun showChangePasswordDialog() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Crea una nueva Contraseña")

        val container = FrameLayout(requireContext())
        val input = EditText(requireContext())
        input.hint = "Nueva Contraseña"
        container.addView(input)

        val padding = (20 * resources.displayMetrics.density).toInt()
        container.setPadding(padding, 0, padding, 0)
        builder.setView(container)

        builder.setPositiveButton("Guardar") { _, _ ->
            val newPasswordWrited = input.text.toString().trim()
            if (newPasswordWrited.isNotEmpty()) {
                binding.changePasswordButton.text = "****"
                newPassword = newPasswordWrited
            }
        }
        builder.setNegativeButton("Cancelar", null)
        builder.show()
    }

    private fun showImageFullScreen() {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_full_screen_image)
        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        val fullScreenImageView = dialog.findViewById<ImageView>(R.id.fullScreenImageView)
        fullScreenImageView.setImageDrawable(binding.profileImageView.drawable)

        fullScreenImageView.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        loadingDialog?.dismiss()
    }
}