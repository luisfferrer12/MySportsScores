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
        val root: View = binding.root

        loadActiveGroupData()

        return root
    }

    private fun loadActiveGroupData() {
        binding.recyclerviewTransform.visibility = View.GONE

        user?.email?.let {
            db.collection("users").document(it).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val activeGroup = document.getString("grupoActivo")

                        if (activeGroup.isNullOrEmpty()) {
                            binding.emptyStateMessage.text = "Aún no tienes deportes registrados para ver tus resultados"
                        } else {
                            binding.emptyStateMessage.text = "Tienes seleccionado: $activeGroup"
                            // TODO: Aquí irá la lógica para cargar los datos del podium en el RecyclerView
                        }
                        binding.emptyStateMessage.visibility = View.VISIBLE
                    } else {
                        // Fallback si el documento del usuario no existe
                        binding.emptyStateMessage.text = "Error al cargar datos del usuario."
                        binding.emptyStateMessage.visibility = View.VISIBLE
                    }
                }
                .addOnFailureListener {
                    binding.emptyStateMessage.text = "Error de conexión. Inténtalo de nuevo."
                    binding.emptyStateMessage.visibility = View.VISIBLE
                }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}