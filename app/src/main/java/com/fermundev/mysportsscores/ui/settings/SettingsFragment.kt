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
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import androidx.core.graphics.drawable.toDrawable

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()
    private val user = FirebaseAuth.getInstance().currentUser
    private val storage = FirebaseStorage.getInstance()
    private var provider: String? = null
    private var activeSport: String? = null

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
        setupSportSection()
        setupActionButtons()

        return binding.root
    }

    private fun rechargeUserInfo() {
        showLoadingDialog(true)
        user?.email?.let { email ->
            db.collection("users").document(email).get()
                .addOnSuccessListener { document ->
                    if (_binding == null) return@addOnSuccessListener
                    if (document != null && document.exists()) {
                        val nickName = document.getString("nickName") ?: ""
                        binding.welcomeTextView.text = getString(R.string.welcome_user, nickName)

                        notificationsStatus = document.getBoolean("notificaciones") ?: true
                        binding.notificationsSwitch.isChecked = notificationsStatus

                        val imageUrl = document.getString("profileImageUrl")
                        if (!imageUrl.isNullOrEmpty()) {
                            Glide.with(this).load(imageUrl).placeholder(R.drawable.avatar_1).error(R.drawable.avatar_1).circleCrop().into(binding.profileImageView)
                        }

                        activeSport = document.getString("grupoActivo")
                        if (activeSport.isNullOrEmpty()) {
                            binding.activeSportTextView.text = getString(R.string.active_sport_none)
                            binding.deleteSportButton.visibility = View.GONE
                        } else {
                            binding.activeSportTextView.text = activeSport
                            binding.deleteSportButton.visibility = View.VISIBLE
                        }
                    }
                    showLoadingDialog(false)
                }
                .addOnFailureListener {
                    if (_binding == null) return@addOnFailureListener
                    showLoadingDialog(false)
                    Toast.makeText(requireContext(), getString(R.string.error_loading_user_data), Toast.LENGTH_SHORT).show()
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
                            if (_binding == null) return@addOnSuccessListener
                            showLoadingDialog(false)
                            Toast.makeText(requireContext(), getString(R.string.success_data_saved), Toast.LENGTH_SHORT).show()
                            findNavController().navigateUp()
                        }
                        .addOnFailureListener { e ->
                            if (_binding == null) return@addOnFailureListener
                            showLoadingDialog(false)
                            Toast.makeText(requireContext(), getString(R.string.error_saving_data, e.message), Toast.LENGTH_SHORT).show()
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
            user?.let { usr ->
                val profileImagesRef = storage.reference.child("MySportsScores/${usr.email}/profile_images/${usr.uid}.jpg")
                profileImagesRef.putFile(newProfileImageUri!!)
                    .addOnSuccessListener { 
                        profileImagesRef.downloadUrl.addOnSuccessListener { downloadUri ->
                            updateFirestore(downloadUri.toString())
                        }
                    }
                    .addOnFailureListener { e ->
                        if (_binding == null) return@addOnFailureListener
                        showLoadingDialog(false)
                        Toast.makeText(requireContext(), getString(R.string.error_uploading_profile_image, e.message), Toast.LENGTH_LONG).show()
                    }
            }
        } else {
            updateFirestore()
        }
    }

    private fun setupSportSection() {
        binding.deleteSportButton.setOnClickListener {
            activeSport?.let { sportName ->
                showDeleteSportConfirmationDialog(sportName)
            }
        }
    }

    private fun showDeleteSportConfirmationDialog(sportName: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.dialog_title_delete_sport))
            .setMessage(getString(R.string.dialog_message_delete_sport, sportName))
            .setPositiveButton(getString(R.string.action_delete)) { _, _ ->
                deleteCurrentSport(sportName)
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    private fun deleteCurrentSport(sportName: String) {
        user?.email?.let { userEmail ->
            showLoadingDialog(true)

            val galleryRef = storage.reference.child("MySportsScores/$userEmail/gallery/$sportName")
            val sportDocRef = db.collection("users").document(userEmail).collection("Deportes").document(sportName)

            galleryRef.listAll().addOnSuccessListener { listResult ->
                val deletePromises = listResult.items.map { it.delete() }
                Tasks.whenAll(deletePromises).addOnCompleteListener { 
                    sportDocRef.collection("Resultados").get().addOnSuccessListener { resultsSnapshot ->
                        val batch = db.batch()
                        resultsSnapshot.documents.forEach { doc -> batch.delete(doc.reference) }
                        batch.commit().addOnCompleteListener {
                            db.collection("users").document(userEmail).get().addOnSuccessListener { userDoc ->
                                val sportsList = userDoc.get("listaDeportes") as? MutableList<String> ?: mutableListOf()
                                sportsList.remove(sportName)
                                val newActiveSport = if (sportsList.isEmpty()) "" else sportsList[0]

                                val finalBatch = db.batch()
                                finalBatch.delete(sportDocRef)
                                finalBatch.update(userDoc.reference, "listaDeportes", sportsList)
                                finalBatch.update(userDoc.reference, "grupoActivo", newActiveSport)
                                finalBatch.commit().addOnSuccessListener {
                                    if (_binding == null) return@addOnSuccessListener
                                    showLoadingDialog(false)
                                    Toast.makeText(context, getString(R.string.success_sport_deleted, sportName), Toast.LENGTH_LONG).show()
                                    rechargeUserInfo()
                                }
                            }
                        }
                    }
                }
            }.addOnFailureListener { e ->
                if (_binding == null) return@addOnFailureListener
                showLoadingDialog(false)
                Toast.makeText(context, getString(R.string.error_deleting_sport, e.message), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showLoadingDialog(show: Boolean) {
        if (show) {
            if (loadingDialog == null) {
                val builder = AlertDialog.Builder(requireContext())
                val progressBar = ProgressBar(requireContext())
                builder.setView(progressBar)
                builder.setCancelable(false)
                loadingDialog = builder.create()
                loadingDialog?.window?.setBackgroundDrawable(android.graphics.Color.TRANSPARENT.toDrawable())
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
                .setTitle(getString(R.string.dialog_title_cancel_settings))
                .setMessage(getString(R.string.dialog_message_cancel_settings))
                .setPositiveButton(getString(R.string.action_discard)) { _, _ -> findNavController().navigateUp() }
                .setNegativeButton(getString(R.string.action_keep_editing), null)
                .show()
        }

        binding.saveButton.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.dialog_title_save_settings))
                .setMessage(getString(R.string.dialog_message_save_settings))
                .setPositiveButton(getString(R.string.action_save)) { _, _ -> saveUserData() }
                .setNegativeButton(getString(R.string.action_keep_editing), null)
                .show()
        }
    }

    private fun showGooglePasswordResetDialog() {
        user?.email?.let { email ->
            FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                .addOnSuccessListener { Toast.makeText(requireContext(), getString(R.string.success_password_reset_email_sent, email), Toast.LENGTH_LONG).show() }
                .addOnFailureListener { e -> Toast.makeText(requireContext(), getString(R.string.error_sending_password_reset_email, e.message), Toast.LENGTH_LONG).show() }
        }
    }

    private fun showEditUsernameDialog() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle(getString(R.string.dialog_title_edit_username))

        val container = FrameLayout(requireContext())
        val input = EditText(requireContext())
        input.hint = binding.welcomeTextView.text.toString().substringAfter("Hola, ")
        container.addView(input)

        val padding = (20 * resources.displayMetrics.density).toInt()
        container.setPadding(padding, 0, padding, 0)
        builder.setView(container)

        builder.setPositiveButton(getString(R.string.action_save)) { _, _ ->
            val newUsernameWrited = input.text.toString().trim()
            if (newUsernameWrited.isNotEmpty()) {
                binding.editUsernameButton.text = newUsernameWrited
                newUsername = newUsernameWrited
            }
        }
        builder.setNegativeButton(getString(R.string.action_cancel), null)
        builder.show()
    }

    private fun showChangePasswordDialog() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle(getString(R.string.dialog_title_create_new_password))

        val container = FrameLayout(requireContext())
        val input = EditText(requireContext())
        input.hint = getString(R.string.hint_new_password)
        container.addView(input)

        val padding = (20 * resources.displayMetrics.density).toInt()
        container.setPadding(padding, 0, padding, 0)
        builder.setView(container)

        builder.setPositiveButton(getString(R.string.action_save)) { _, _ ->
            val newPasswordWrited = input.text.toString().trim()
            if (newPasswordWrited.isNotEmpty()) {
                binding.changePasswordButton.text = "****"
                newPassword = newPasswordWrited
            }
        }
        builder.setNegativeButton(getString(R.string.action_cancel), null)
        builder.show()
    }

    private fun showImageFullScreen() {
        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_full_screen_image)
        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        val fullScreenImageView = dialog.findViewById<ImageView>(R.id.full_screen_imageview)
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