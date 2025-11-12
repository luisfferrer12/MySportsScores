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
import com.fermundev.mysportsscores.databinding.DialogAddPerformanceResultBinding
import com.fermundev.mysportsscores.databinding.FragmentResultsBinding
import com.fermundev.mysportsscores.databinding.ItemPerformanceResultBinding
import com.fermundev.mysportsscores.databinding.ItemResultBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Locale

// Sealed class to represent the different types of results
sealed class ResultItem {
    abstract val id: String
    data class OppositionResult(val snapshot: DocumentSnapshot) : ResultItem() {
        override val id: String = snapshot.id
    }
    data class PerformanceResult(val snapshot: DocumentSnapshot) : ResultItem() {
        override val id: String = snapshot.id
    }
}

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
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        loadResults()
    }

    private fun setupRecyclerView() {
        resultsAdapter = ResultsAdapter(
            onEditClicked = { result -> 
                when(result) {
                    is ResultItem.OppositionResult -> showEditResultDialog(result.snapshot)
                    is ResultItem.PerformanceResult -> showEditPerformanceResultDialog(result.snapshot)
                }
            },
            onDeleteClicked = { result -> 
                when(result) {
                    is ResultItem.OppositionResult -> showDeleteConfirmationDialog(result.snapshot)
                    is ResultItem.PerformanceResult -> showDeletePerformanceResultConfirmationDialog(result.snapshot)
                }
            }
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
                            val resultItems = resultsSnapshot.documents.map {
                                when (it.getString("type")) {
                                    "PERFORMANCE" -> ResultItem.PerformanceResult(it)
                                    else -> ResultItem.OppositionResult(it)
                                }
                            }
                            binding.resultsRecyclerview.visibility = View.VISIBLE
                            binding.emptyResultsMessage.visibility = View.GONE
                            resultsAdapter.updateData(resultItems)
                        }
                    }
                    .addOnFailureListener { 
                        if (_binding == null) return@addOnFailureListener
                        showEmptyState(getString(R.string.error_loading_results)) 
                    }
            }
        }
    }

    private fun showEditPerformanceResultDialog(result: DocumentSnapshot) {
        user?.email?.let { email ->
            activeSport?.let { sport ->
                val sportDocRef = db.collection("users").document(email).collection("Deportes").document(sport)
                sportDocRef.get().addOnSuccessListener { sportDoc ->
                    val players = sportDoc.get("jugadores") as? List<String> ?: emptyList()
                    val dialogBinding = DialogAddPerformanceResultBinding.inflate(layoutInflater)
                    val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, players)
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    dialogBinding.playerSpinner.adapter = adapter

                    // Pre-fill dialog with existing data
                    val oldEventName = result.getString("eventName")
                    val oldPlayer = result.getString("player")
                    val oldResult = result.getDouble("result")
                    dialogBinding.eventNameEditText.setText(oldEventName)
                    dialogBinding.playerSpinner.setSelection(adapter.getPosition(oldPlayer))
                    dialogBinding.resultEditText.setText(oldResult?.toString())

                    AlertDialog.Builder(requireContext())
                        .setTitle("Editar Resultado de Rendimiento")
                        .setView(dialogBinding.root)
                        .setPositiveButton(getString(R.string.action_save)) { _, _ ->
                            val newEventName = dialogBinding.eventNameEditText.text.toString().trim()
                            val newPlayer = dialogBinding.playerSpinner.selectedItem.toString()
                            val newResult = dialogBinding.resultEditText.text.toString().toDoubleOrNull()

                            if (newEventName.isNotEmpty() && newResult != null) {
                                updatePerformanceResult(result, newEventName, newPlayer, newResult)
                            }
                        }
                        .setNegativeButton(getString(R.string.action_cancel), null)
                        .show()
                }
            }
        }
    }

    private fun updatePerformanceResult(result: DocumentSnapshot, newEventName: String, newPlayer: String, newResult: Double) {
        val updates = hashMapOf<String, Any>(
            "eventName" to newEventName,
            "player" to newPlayer,
            "result" to newResult
        )
        result.reference.update(updates).addOnSuccessListener {
            if (_binding == null) return@addOnSuccessListener
            Toast.makeText(requireContext(), getString(R.string.success_result_updated), Toast.LENGTH_SHORT).show()
            loadResults()
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

                    val winPoints = sportDoc.getLong("points_win") ?: 3L
                    val drawPoints = sportDoc.getLong("points_draw") ?: 1L
                    val lossPoints = sportDoc.getLong("points_loss") ?: 0L

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
                    val oldP1Score = result.getLong("puntosJugador1")
                    val oldP2Score = result.getLong("puntosJugador2")

                    player1Spinner.setSelection(adapter.getPosition(oldP1))
                    player2Spinner.setSelection(adapter.getPosition(oldP2))
                    player1Points.setText(oldP1Score?.toString())
                    player2Points.setText(oldP2Score?.toString())

                    val dialog = AlertDialog.Builder(requireContext())
                        .setTitle(getString(R.string.dialog_title_edit_result))
                        .setView(dialogView)
                        .create()

                    saveButton.setOnClickListener {
                        val newP1 = player1Spinner.selectedItem.toString()
                        val newP2 = player2Spinner.selectedItem.toString()
                        val newP1Score = player1Points.text.toString().toLongOrNull()
                        val newP2Score = player2Points.text.toString().toLongOrNull()

                        if (newP1 != newP2 && newP1Score != null && newP2Score != null && oldP1 != null && oldP2 != null && oldP1Score != null && oldP2Score != null) {
                            updateResult(result, players, oldP1, oldP2, oldP1Score, oldP2Score, newP1, newP2, newP1Score, newP2Score, winPoints, drawPoints, lossPoints)
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
        oldP1: String, oldP2: String, oldP1Score: Long, oldP2Score: Long, 
        newP1: String, newP2: String, newP1Score: Long, newP2Score: Long,
        winP: Long, drawP: Long, lossP: Long
    ) {
        val sportDocRef = result.reference.parent.parent ?: return
        val pointChanges = mutableMapOf<String, Long>()

        // Revert old points
        if (oldP1Score == oldP2Score) {
            if (officialPlayers.contains(oldP1)) { pointChanges[oldP1] = (pointChanges[oldP1] ?: 0) - drawP }
            if (officialPlayers.contains(oldP2)) { pointChanges[oldP2] = (pointChanges[oldP2] ?: 0) - drawP }
        } else {
            val (oldWinner, oldLoser) = if (oldP1Score > oldP2Score) oldP1 to oldP2 else oldP2 to oldP1
            if (officialPlayers.contains(oldWinner)) { pointChanges[oldWinner] = (pointChanges[oldWinner] ?: 0) - winP }
            if (officialPlayers.contains(oldLoser)) { pointChanges[oldLoser] = (pointChanges[oldLoser] ?: 0) - lossP }
        }

        // Apply new points
        if (newP1Score == newP2Score) {
            pointChanges[newP1] = (pointChanges[newP1] ?: 0) + drawP
            pointChanges[newP2] = (pointChanges[newP2] ?: 0) + drawP
        } else {
            val (newWinner, newLoser) = if (newP1Score > newP2Score) newP1 to newP2 else newP2 to newP1
            pointChanges[newWinner] = (pointChanges[newWinner] ?: 0) + winP
            pointChanges[newLoser] = (pointChanges[newLoser] ?: 0) + lossP
        }

        db.runBatch { batch ->
            for ((player, change) in pointChanges) {
                if (change != 0L && officialPlayers.contains(player)) {
                    batch.update(sportDocRef, "ranking.$player", FieldValue.increment(change))
                }
            }
            val newResultData = mapOf("jugador1" to newP1, "jugador2" to newP2, "puntosJugador1" to newP1Score, "puntosJugador2" to newP2Score)
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

     private fun showDeletePerformanceResultConfirmationDialog(result: DocumentSnapshot) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.dialog_title_delete_result))
            .setMessage(getString(R.string.dialog_message_delete_result)) // We can reuse the same message
            .setPositiveButton(getString(R.string.action_delete)) { _, _ ->
                deletePerformanceResult(result)
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
                    val winPoints = sportDoc.getLong("points_win") ?: 3L
                    val drawPoints = sportDoc.getLong("points_draw") ?: 1L
                    val lossPoints = sportDoc.getLong("points_loss") ?: 0L

                    val p1 = result.getString("jugador1") ?: return@addOnSuccessListener
                    val p2 = result.getString("jugador2") ?: return@addOnSuccessListener
                    val p1Score = result.getLong("puntosJugador1") ?: 0L
                    val p2Score = result.getLong("puntosJugador2") ?: 0L

                    db.runBatch { batch ->
                        if (p1Score == p2Score) {
                            if (officialPlayers.contains(p1)) { batch.update(sportDocRef, "ranking.$p1", FieldValue.increment(-drawPoints)) }
                            if (officialPlayers.contains(p2)) { batch.update(sportDocRef, "ranking.$p2", FieldValue.increment(-drawPoints)) }
                        } else {
                            val (winner, loser) = if (p1Score > p2Score) p1 to p2 else p2 to p1
                            if (officialPlayers.contains(winner)) { batch.update(sportDocRef, "ranking.$winner", FieldValue.increment(-winPoints)) }
                            if (officialPlayers.contains(loser)) { batch.update(sportDocRef, "ranking.$loser", FieldValue.increment(-lossPoints)) }
                        }
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

    private fun deletePerformanceResult(result: DocumentSnapshot) {
        result.reference.delete()
            .addOnSuccessListener { 
                if (_binding == null) return@addOnSuccessListener
                Toast.makeText(requireContext(), getString(R.string.success_result_deleted), Toast.LENGTH_SHORT).show()
                loadResults() 
            }
            .addOnFailureListener { e ->
                if (_binding == null) return@addOnFailureListener
                Toast.makeText(requireContext(), getString(R.string.error_deleting_result, e.message), Toast.LENGTH_LONG).show()
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

private const val TYPE_OPPOSITION = 0
private const val TYPE_PERFORMANCE = 1

class ResultsAdapter(
    private var results: List<ResultItem> = emptyList(),
    private val onEditClicked: (ResultItem) -> Unit,
    private val onDeleteClicked: (ResultItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun getItemViewType(position: Int): Int {
        return when (results[position]) {
            is ResultItem.OppositionResult -> TYPE_OPPOSITION
            is ResultItem.PerformanceResult -> TYPE_PERFORMANCE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_OPPOSITION -> {
                val binding = ItemResultBinding.inflate(inflater, parent, false)
                OppositionViewHolder(binding)
            }
            TYPE_PERFORMANCE -> {
                val binding = ItemPerformanceResultBinding.inflate(inflater, parent, false)
                PerformanceViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = results[position]) {
            is ResultItem.OppositionResult -> (holder as OppositionViewHolder).bind(item)
            is ResultItem.PerformanceResult -> (holder as PerformanceViewHolder).bind(item)
        }
    }

    override fun getItemCount(): Int = results.size

    fun updateData(newResults: List<ResultItem>) {
        results = newResults
        notifyDataSetChanged()
    }

    inner class OppositionViewHolder(private val binding: ItemResultBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(resultItem: ResultItem.OppositionResult) {
            val result = resultItem.snapshot
            val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

            binding.matchNumberTextview.text = result.id
            binding.player1NameTextview.text = result.getString("jugador1")
            binding.player2NameTextview.text = result.getString("jugador2")
            binding.player1ScoreTextview.text = result.getLong("puntosJugador1")?.toString() ?: "0"
            binding.player2ScoreTextview.text = result.getLong("puntosJugador2")?.toString() ?: "0"
            
            val timestamp = result.getTimestamp("timestamp")
            binding.matchDateTextview.text = timestamp?.toDate()?.let { dateFormat.format(it) } ?: "--"

            binding.editButton.setOnClickListener { onEditClicked(resultItem) }
            binding.deleteButton.setOnClickListener { onDeleteClicked(resultItem) }
        }
    }

    inner class PerformanceViewHolder(private val binding: ItemPerformanceResultBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(resultItem: ResultItem.PerformanceResult) {
            val result = resultItem.snapshot
            val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

            binding.eventNameTextview.text = result.getString("eventName")
            binding.playerNameTextview.text = result.getString("player")
            binding.resultTextview.text = result.getDouble("result")?.toString() ?: "--"

            val timestamp = result.getTimestamp("timestamp")
            binding.matchDateTextview.text = timestamp?.toDate()?.let { dateFormat.format(it) } ?: "--"

            binding.editButton.setOnClickListener { onEditClicked(resultItem) }
            binding.deleteButton.setOnClickListener { onDeleteClicked(resultItem) }
        }
    }
}