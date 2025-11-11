package com.fermundev.mysportsscores.ui.results

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fermundev.mysportsscores.databinding.FragmentResultsBinding
import com.fermundev.mysportsscores.databinding.ItemResultBinding
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Locale

class ResultsFragment : Fragment() {

    private var _binding: FragmentResultsBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()
    private val user = FirebaseAuth.getInstance().currentUser
    private lateinit var resultsAdapter: ResultsAdapter
    private var activeSport: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentResultsBinding.inflate(inflater, container, false)
        
        setupRecyclerView()
        loadResults()

        return binding.root
    }

    private fun setupRecyclerView() {
        resultsAdapter = ResultsAdapter(emptyList(),
            onEditClicked = { resultId -> Toast.makeText(requireContext(), "Editar: $resultId", Toast.LENGTH_SHORT).show() },
            onDeleteClicked = { result -> showDeleteConfirmationDialog(result) }
        )
        binding.resultsRecyclerview.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = resultsAdapter
        }
    }

    private fun loadResults() {
        user?.email?.let { email ->
            db.collection("users").document(email).get().addOnSuccessListener { userDoc ->
                activeSport = userDoc.getString("grupoActivo")
                if (activeSport.isNullOrEmpty()) {
                    showEmptyState("No has seleccionado un deporte activo.")
                    return@addOnSuccessListener
                }

                db.collection("users").document(email)
                    .collection("Deportes").document(activeSport!!)
                    .collection("Resultados")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .get()
                    .addOnSuccessListener { resultsSnapshot ->
                        if (resultsSnapshot.isEmpty) {
                            showEmptyState("No hay resultados para este deporte.")
                        } else {
                            binding.resultsRecyclerview.visibility = View.VISIBLE
                            binding.emptyResultsMessage.visibility = View.GONE
                            resultsAdapter.updateData(resultsSnapshot.documents)
                        }
                    }
                    .addOnFailureListener { showEmptyState("Error al cargar resultados.") }
            }
        }
    }

    private fun showDeleteConfirmationDialog(result: DocumentSnapshot) {
        AlertDialog.Builder(requireContext())
            .setTitle("Eliminar Resultado")
            .setMessage("¿Seguro que deseas eliminar este resultado? Esta acción no se puede deshacer y afectará al ranking.")
            .setPositiveButton("Eliminar") { _, _ ->
                deleteResult(result)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun deleteResult(result: DocumentSnapshot) {
        val p1 = result.getString("jugador1") ?: return
        val p2 = result.getString("jugador2") ?: return
        val p1Points = result.getLong("puntosJugador1") ?: 0
        val p2Points = result.getLong("puntosJugador2") ?: 0

        val (winner, loser) = if (p1Points > p2Points) p1 to p2 else p2 to p1
        
        if (user?.email == null || activeSport == null) return

        val sportDocRef = db.collection("users").document(user.email!!)
            .collection("Deportes").document(activeSport!!)

        db.runBatch { batch ->
            // 1. Revertir puntos en el ranking
            batch.update(sportDocRef, "ranking.$winner", FieldValue.increment(-3))
            batch.update(sportDocRef, "ranking.$loser", FieldValue.increment(-1))

            // 2. Eliminar el documento del resultado
            batch.delete(result.reference)
        }.addOnSuccessListener {
            Toast.makeText(requireContext(), "Resultado eliminado y ranking actualizado", Toast.LENGTH_SHORT).show()
            loadResults() // Recargar la lista
        }.addOnFailureListener { e ->
            Toast.makeText(requireContext(), "Error al eliminar: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showEmptyState(message: String) {
        binding.resultsRecyclerview.visibility = View.GONE
        binding.emptyResultsMessage.visibility = View.VISIBLE
        binding.emptyResultsMessage.text = message
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class ResultsAdapter(
    private var results: List<DocumentSnapshot>,
    private val onEditClicked: (String) -> Unit,
    private val onDeleteClicked: (DocumentSnapshot) -> Unit
) : RecyclerView.Adapter<ResultsAdapter.ResultViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResultViewHolder {
        val binding = ItemResultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ResultViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ResultViewHolder, position: Int) {
        holder.bind(results[position])
    }

    override fun getItemCount(): Int = results.size

    fun updateData(newResults: List<DocumentSnapshot>) {
        results = newResults
        notifyDataSetChanged()
    }

    inner class ResultViewHolder(private val binding: ItemResultBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(result: DocumentSnapshot) {
            val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

            binding.matchNumberTextview.text = result.id
            binding.player1NameTextview.text = result.getString("jugador1")
            binding.player2NameTextview.text = result.getString("jugador2")
            binding.player1ScoreTextview.text = result.getLong("puntosJugador1")?.toString() ?: "0"
            binding.player2ScoreTextview.text = result.getLong("puntosJugador2")?.toString() ?: "0"
            
            val timestamp = result.getTimestamp("timestamp")
            binding.matchDateTextview.text = timestamp?.toDate()?.let { dateFormat.format(it) } ?: "--"

            binding.editButton.setOnClickListener { onEditClicked(result.id) }
            binding.deleteButton.setOnClickListener { onDeleteClicked(result) }
        }
    }
}