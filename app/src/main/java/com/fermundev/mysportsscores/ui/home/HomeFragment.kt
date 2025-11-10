package com.fermundev.mysportsscores.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.fermundev.mysportsscores.databinding.FragmentHomeBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()
    private val user = FirebaseAuth.getInstance().currentUser

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        loadActiveGroupData()
        return binding.root
    }

    private fun loadActiveGroupData() {
        user?.email?.let {
            db.collection("users").document(it).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val activeGroup = document.getString("grupoActivo")

                        if (activeGroup.isNullOrEmpty()) {
                            // No hay deporte activo, mostrar mensaje principal
                            binding.emptyStateMessage.visibility = View.VISIBLE
                            binding.mainContentGroup?.visibility = View.GONE
                        } else {
                            // Hay un deporte activo, mostrar contenido principal
                            binding.emptyStateMessage.visibility = View.GONE
                            binding.mainContentGroup?.visibility = View.VISIBLE
                            binding.sportTitleTextview?.text = activeGroup

                            // TODO: Lógica para cargar los resultados del deporte activo

                            // Por ahora, asumimos que no hay resultados
                            binding.rankingRecyclerview?.visibility = View.GONE
                            binding.emptyRankingMessage?.visibility = View.VISIBLE
                        }
                    } else {
                        binding.emptyStateMessage.visibility = View.VISIBLE
                        binding.mainContentGroup?.visibility = View.GONE
                    }
                }
                .addOnFailureListener {
                    binding.emptyStateMessage.text = "Error de conexión"
                    binding.emptyStateMessage.visibility = View.VISIBLE
                    binding.mainContentGroup?.visibility = View.GONE
                }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
