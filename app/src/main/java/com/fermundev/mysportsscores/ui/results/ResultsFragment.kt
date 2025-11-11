package com.fermundev.mysportsscores.ui.results

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fermundev.mysportsscores.R
import com.fermundev.mysportsscores.databinding.FragmentResultsBinding
import com.fermundev.mysportsscores.databinding.ItemResultBinding
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
            onEditClicked = { result -> showEditResultDialog(result) },
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

    private fun showEditResultDialog(result: DocumentSnapshot) {
        if (user?.email == null || activeSport == null) return

        val sportDocRef = db.collection("users").document(user.email!!).collection("Deportes").document(activeSport!!)
        
        sportDocRef.get().addOnSuccessListener { sportDoc ->
            val players = sportDoc.get("jugadores") as? List<String> ?: emptyList()
            if (players.isEmpty()) {
                Toast.makeText(context, "No hay jugadores en este deporte.", Toast.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }

            val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_result, null)
            val player1Spinner = dialogView.findViewById<Spinner>(R.id.player1_spinner)
            val player2Spinner = dialogView.findViewById<Spinner>(R.id.player2_spinner)
            val player1Points = dialogView.findViewById<EditText>(R.id.player1_points_edittext)
            val player2Points = dialogView.findViewById<EditText>(R.id.player2_points_edittext)
            dialogView.findViewById<View>(R.id.add_player_button).visibility = View.GONE // Ocultar al editar
            dialogView.findViewById<View>(R.id.new_player_name_edittext).visibility = View.GONE
            val saveButton = dialogView.findViewById<Button>(R.id.save_button)
            val cancelButton = dialogView.findViewById<Button>(R.id.cancel_button)

            // Poblar spinners
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, players)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            player1Spinner.adapter = adapter
            player2Spinner.adapter = adapter

            // Pre-llenar datos del resultado a editar
            val oldP1 = result.getString("jugador1")
            val oldP2 = result.getString("jugador2")
            val oldP1Points = result.getLong("puntosJugador1")
            val oldP2Points = result.getLong("puntosJugador2")

            player1Spinner.setSelection(adapter.getPosition(oldP1))
            player2Spinner.setSelection(adapter.getPosition(oldP2))
            player1Points.setText(oldP1Points?.toString())
            player2Points.setText(oldP2Points?.toString())

            val dialog = AlertDialog.Builder(context)
                .setTitle("Editar Resultado")
                .setView(dialogView)
                .create()

            saveButton.setOnClickListener {
                val newP1 = player1Spinner.selectedItem.toString()
                val newP2 = player2Spinner.selectedItem.toString()
                val newP1Points = player1Points.text.toString().toLongOrNull()
                val newP2Points = player2Points.text.toString().toLongOrNull()

                if (newP1 != newP2 && newP1Points != null && newP2Points != null) {
                    updateResult(result, oldP1!!, oldP2!!, oldP1Points!!, oldP2Points!!, newP1, newP2, newP1Points, newP2Points)
                    dialog.dismiss()
                } else {
                    Toast.makeText(context, "Datos inválidos", Toast.LENGTH_SHORT).show()
                }
            }
            cancelButton.setOnClickListener { dialog.dismiss() }

            dialog.show()
        }
    }

    private fun updateResult(
        result: DocumentSnapshot, 
        oldP1: String, oldP2: String, oldP1Points: Long, oldP2Points: Long, 
        newP1: String, newP2: String, newP1Points: Long, newP2Points: Long
    ) {
        val sportDocRef = result.reference.parent.parent!!
        val pointChanges = mutableMapOf<String, Long>()

        // 1. Revertir puntos antiguos
        val (oldWinner, oldLoser) = if (oldP1Points > oldP2Points) oldP1 to oldP2 else oldP2 to oldP1
        pointChanges[oldWinner] = (pointChanges[oldWinner] ?: 0) - 3
        pointChanges[oldLoser] = (pointChanges[oldLoser] ?: 0) - 1

        // 2. Aplicar puntos nuevos
        val (newWinner, newLoser) = if (newP1Points > newP2Points) newP1 to newP2 else newP2 to newP1
        pointChanges[newWinner] = (pointChanges[newWinner] ?: 0) + 3
        pointChanges[newLoser] = (pointChanges[newLoser] ?: 0) + 1

        db.runBatch { batch ->
            // 3. Actualizar el ranking
            for ((player, change) in pointChanges) {
                if (change != 0L) {
                    batch.update(sportDocRef, "ranking.$player", FieldValue.increment(change))
                }
            }
            // 4. Actualizar el documento del resultado
            val newResultData = mapOf(
                "jugador1" to newP1,
                "jugador2" to newP2,
                "puntosJugador1" to newP1Points,
                "puntosJugador2" to newP2Points
            )
            batch.update(result.reference, newResultData)
        }.addOnSuccessListener {
            Toast.makeText(context, "Resultado actualizado", Toast.LENGTH_SHORT).show()
            loadResults()
        }.addOnFailureListener { e ->
            Toast.makeText(context, "Error al actualizar: ${e.message}", Toast.LENGTH_LONG).show()
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
    private val onEditClicked: (DocumentSnapshot) -> Unit,
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

            binding.editButton.setOnClickListener { onEditClicked(result) }
            binding.deleteButton.setOnClickListener { onDeleteClicked(result) }
        }
    }
}