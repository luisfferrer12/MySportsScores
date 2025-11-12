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
                if (_binding == null) return@addOnSuccessListener
                activeSport = userDoc.getString("grupoActivo")
                val sport = activeSport
                if (sport.isNullOrEmpty()) {
                    showEmptyState(getString(R.string.error_no_active_sport_results))
                    return@addOnSuccessListener
                }

                db.collection("users").document(email)
                    .collection("Deportes").document(sport)
                    .collection("Resultados")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .get()
                    .addOnSuccessListener { resultsSnapshot ->
                        if (_binding == null) return@addOnSuccessListener
                        if (resultsSnapshot.isEmpty) {
                            showEmptyState(getString(R.string.empty_state_no_results))
                        } else {
                            binding.resultsRecyclerview.visibility = View.VISIBLE
                            binding.emptyResultsMessage.visibility = View.GONE
                            resultsAdapter.updateData(resultsSnapshot.documents)
                        }
                    }
                    .addOnFailureListener { 
                        if (_binding == null) return@addOnFailureListener
                        showEmptyState(getString(R.string.error_loading_results)) 
                    }
            }
        }
    }

    private fun showEditResultDialog(result: DocumentSnapshot) {
        user?.email?.let { email ->
            activeSport?.let { sport ->
                val sportDocRef = db.collection("users").document(email).collection("Deportes").document(sport)
                sportDocRef.get().addOnSuccessListener { sportDoc ->
                    if (_binding == null) return@addOnSuccessListener
                    val players = sportDoc.get("jugadores") as? List<String> ?: emptyList()
                    if (players.isEmpty()) {
                        Toast.makeText(requireContext(), getString(R.string.error_no_players_in_sport), Toast.LENGTH_SHORT).show()
                        return@addOnSuccessListener
                    }

                    val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_result, null)
                    val player1Spinner = dialogView.findViewById<Spinner>(R.id.player1_spinner)
                    val player2Spinner = dialogView.findViewById<Spinner>(R.id.player2_spinner)
                    val player1Points = dialogView.findViewById<EditText>(R.id.player1_points_edittext)
                    val player2Points = dialogView.findViewById<EditText>(R.id.player2_points_edittext)
                    val saveButton = dialogView.findViewById<Button>(R.id.save_button)
                    val cancelButton = dialogView.findViewById<Button>(R.id.cancel_button)

                    val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, players)
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    player1Spinner.adapter = adapter
                    player2Spinner.adapter = adapter

                    val oldP1 = result.getString("jugador1")
                    val oldP2 = result.getString("jugador2")
                    val oldP1Points = result.getLong("puntosJugador1")
                    val oldP2Points = result.getLong("puntosJugador2")

                    player1Spinner.setSelection(adapter.getPosition(oldP1))
                    player2Spinner.setSelection(adapter.getPosition(oldP2))
                    player1Points.setText(oldP1Points?.toString())
                    player2Points.setText(oldP2Points?.toString())

                    val dialog = AlertDialog.Builder(requireContext())
                        .setTitle(getString(R.string.dialog_title_edit_result))
                        .setView(dialogView)
                        .create()

                    saveButton.setOnClickListener {
                        val newP1 = player1Spinner.selectedItem.toString()
                        val newP2 = player2Spinner.selectedItem.toString()
                        val newP1Points = player1Points.text.toString().toLongOrNull()
                        val newP2Points = player2Points.text.toString().toLongOrNull()

                        if (newP1 != newP2 && newP1Points != null && newP2Points != null && oldP1 != null && oldP2 != null && oldP1Points != null && oldP2Points != null) {
                            updateResult(result, players, oldP1, oldP2, oldP1Points, oldP2Points, newP1, newP2, newP1Points, newP2Points)
                            dialog.dismiss()
                        } else {
                            Toast.makeText(requireContext(), getString(R.string.error_invalid_data), Toast.LENGTH_SHORT).show()
                        }
                    }
                    cancelButton.setOnClickListener { dialog.dismiss() }

                    dialog.show()
                }
            }
        }
    }

    private fun updateResult(
        result: DocumentSnapshot, 
        officialPlayers: List<String>,
        oldP1: String, oldP2: String, oldP1Points: Long, oldP2Points: Long, 
        newP1: String, newP2: String, newP1Points: Long, newP2Points: Long
    ) {
        val sportDocRef = result.reference.parent.parent ?: return
        val pointChanges = mutableMapOf<String, Long>()

        val (oldWinner, oldLoser) = if (oldP1Points > oldP2Points) oldP1 to oldP2 else oldP2 to oldP1
        if (officialPlayers.contains(oldWinner)) { pointChanges[oldWinner] = (pointChanges[oldWinner] ?: 0) - 3 }
        if (officialPlayers.contains(oldLoser)) { pointChanges[oldLoser] = (pointChanges[oldLoser] ?: 0) - 1 }

        val (newWinner, newLoser) = if (newP1Points > newP2Points) newP1 to newP2 else newP2 to newP1
        pointChanges[newWinner] = (pointChanges[newWinner] ?: 0) + 3
        pointChanges[newLoser] = (pointChanges[newLoser] ?: 0) + 1

        db.runBatch { batch ->
            for ((player, change) in pointChanges) {
                if (change != 0L && officialPlayers.contains(player)) {
                    batch.update(sportDocRef, "ranking.$player", FieldValue.increment(change))
                }
            }
            val newResultData = mapOf("jugador1" to newP1, "jugador2" to newP2, "puntosJugador1" to newP1Points, "puntosJugador2" to newP2Points)
            batch.update(result.reference, newResultData)
        }.addOnSuccessListener {
            if (_binding == null) return@addOnSuccessListener
            Toast.makeText(requireContext(), getString(R.string.success_result_updated), Toast.LENGTH_SHORT).show()
            loadResults()
        }.addOnFailureListener { e ->
            if (_binding == null) return@addOnFailureListener
            Toast.makeText(requireContext(), getString(R.string.error_updating_result, e.message), Toast.LENGTH_LONG).show()
        }
    }

    private fun showDeleteConfirmationDialog(result: DocumentSnapshot) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.dialog_title_delete_result))
            .setMessage(getString(R.string.dialog_message_delete_result))
            .setPositiveButton(getString(R.string.action_delete)) { _, _ ->
                deleteResult(result)
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    private fun deleteResult(result: DocumentSnapshot) {
        user?.email?.let { email ->
            activeSport?.let { sport ->
                val sportDocRef = db.collection("users").document(email).collection("Deportes").document(sport)
                sportDocRef.get().addOnSuccessListener { sportDoc ->
                    if (_binding == null) return@addOnSuccessListener
                    val officialPlayers = sportDoc.get("jugadores") as? List<String> ?: emptyList()

                    val p1 = result.getString("jugador1") ?: return@addOnSuccessListener
                    val p2 = result.getString("jugador2") ?: return@addOnSuccessListener
                    val p1Points = result.getLong("puntosJugador1") ?: 0
                    val p2Points = result.getLong("puntosJugador2") ?: 0

                    val (winner, loser) = if (p1Points > p2Points) p1 to p2 else p2 to p1

                    db.runBatch { batch ->
                        if (officialPlayers.contains(winner)) { batch.update(sportDocRef, "ranking.$winner", FieldValue.increment(-3)) }
                        if (officialPlayers.contains(loser)) { batch.update(sportDocRef, "ranking.$loser", FieldValue.increment(-1)) }
                        batch.delete(result.reference)
                    }.addOnSuccessListener {
                        if (_binding == null) return@addOnSuccessListener
                        Toast.makeText(requireContext(), getString(R.string.success_result_deleted), Toast.LENGTH_SHORT).show()
                        loadResults()
                    }.addOnFailureListener { e ->
                        if (_binding == null) return@addOnFailureListener
                        Toast.makeText(requireContext(), getString(R.string.error_deleting_result, e.message), Toast.LENGTH_LONG).show()
                    }
                }
            }
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