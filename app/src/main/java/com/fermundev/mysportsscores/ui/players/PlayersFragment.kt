package com.fermundev.mysportsscores.ui.players

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fermundev.mysportsscores.R
import com.fermundev.mysportsscores.databinding.FragmentPlayersBinding
import com.fermundev.mysportsscores.databinding.ItemPlayerBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import androidx.core.graphics.drawable.toDrawable

class PlayersFragment : Fragment() {

    private var _binding: FragmentPlayersBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()
    private val user = FirebaseAuth.getInstance().currentUser
    private lateinit var playersAdapter: PlayersAdapter
    private var activeSport: String? = null
    private var loadingDialog: AlertDialog? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlayersBinding.inflate(inflater, container, false)
        
        setupRecyclerView()
        loadPlayersData()

        binding.addPlayerFab.setOnClickListener {
            showAddPlayerDialog()
        }

        return binding.root
    }

    private fun setupRecyclerView() {
        playersAdapter = PlayersAdapter(
            emptyList(), 
            onEditClicked = { oldName -> showEditPlayerDialog(oldName) },
            onDeleteClicked = { player -> showDeletePlayerConfirmationDialog(player) }
        )
        binding.playersRecyclerview.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = playersAdapter
        }
    }

    private fun loadPlayersData() {
        user?.email?.let { email ->
            db.collection("users").document(email).get().addOnSuccessListener { userDoc ->
                if (_binding == null) return@addOnSuccessListener
                activeSport = userDoc.getString("grupoActivo")
                if (activeSport.isNullOrEmpty()) {
                    showEmptyState(getString(R.string.error_no_active_sport_players))
                    binding.playersTitle.text = getString(R.string.menu_players)
                    return@addOnSuccessListener
                }

                binding.playersTitle.text = getString(R.string.players_of_sport, activeSport)
                val sportDocRef = db.collection("users").document(email)
                    .collection("Deportes").document(activeSport!!)

                sportDocRef.get().addOnSuccessListener { sportSnapshot ->
                    if (_binding == null) return@addOnSuccessListener
                    val players = sportSnapshot.get("jugadores") as? List<String> ?: emptyList()
                    if (players.isEmpty()) {
                        showEmptyState(getString(R.string.empty_state_no_players))
                    } else {
                        binding.playersRecyclerview.visibility = View.VISIBLE
                        binding.emptyPlayersMessage.visibility = View.GONE
                        playersAdapter.updateData(players)
                    }
                }
            }
        }
    }

    private fun showAddPlayerDialog() {
        if (activeSport.isNullOrEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.error_select_sport_first), Toast.LENGTH_SHORT).show()
            return
        }

        val editText = EditText(requireContext())
        editText.hint = getString(R.string.hint_player_name)

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.dialog_title_add_player))
            .setView(editText)
            .setPositiveButton(getString(R.string.action_add)) { _, _ ->
                val newPlayerName = editText.text.toString().trim()
                if (newPlayerName.isNotEmpty()) {
                    addPlayerToSport(newPlayerName)
                }
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }
    
    private fun showEditPlayerDialog(oldName: String) {
        val editText = EditText(requireContext())
        editText.setText(oldName)

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.dialog_title_edit_player))
            .setView(editText)
            .setPositiveButton(getString(R.string.action_save)) { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty() && newName != oldName) {
                    updatePlayerName(oldName, newName)
                }
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    private fun updatePlayerName(oldName: String, newName: String) {
        user?.email?.let { email ->
            activeSport?.let { sport ->
                showLoadingDialog(true)

                val sportDocRef = db.collection("users").document(email)
                    .collection("Deportes").document(sport)
                val resultsCollectionRef = sportDocRef.collection("Resultados")

                sportDocRef.get().addOnSuccessListener { sportDoc ->
                    val players = sportDoc.get("jugadores") as? List<String> ?: emptyList()
                    val ranking = sportDoc.get("ranking") as? Map<String, Long> ?: emptyMap()

                    resultsCollectionRef.get().addOnSuccessListener { resultsSnapshot ->
                        db.runBatch { batch ->
                            val score = ranking[oldName] ?: 0L
                            val newRanking = ranking.toMutableMap()
                            newRanking.remove(oldName)
                            newRanking[newName] = score
                            batch.update(sportDocRef, "ranking", newRanking)

                            val newPlayersList = players.map { if (it == oldName) newName else it }
                            batch.update(sportDocRef, "jugadores", newPlayersList)

                            for (resultDoc in resultsSnapshot.documents) {
                                if (resultDoc.getString("jugador1") == oldName) {
                                    batch.update(resultDoc.reference, "jugador1", newName)
                                }
                                if (resultDoc.getString("jugador2") == oldName) {
                                    batch.update(resultDoc.reference, "jugador2", newName)
                                }
                            }
                        }.addOnSuccessListener {
                            if (_binding == null) return@addOnSuccessListener
                            showLoadingDialog(false)
                            Toast.makeText(requireContext(), getString(R.string.success_player_updated), Toast.LENGTH_SHORT).show()
                            loadPlayersData()
                        }.addOnFailureListener { e ->
                            if (_binding == null) return@addOnFailureListener
                            showLoadingDialog(false)
                            Toast.makeText(requireContext(), getString(R.string.error_updating_player, e.message), Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun addPlayerToSport(playerName: String) {
        user?.email?.let { email ->
            activeSport?.let { sport ->
                val sportDocRef = db.collection("users").document(email)
                    .collection("Deportes").document(sport)

                db.runBatch { batch ->
                    batch.update(sportDocRef, "jugadores", FieldValue.arrayUnion(playerName))
                    batch.update(sportDocRef, "ranking.$playerName", 0)
                }.addOnSuccessListener { 
                    if (_binding == null) return@addOnSuccessListener
                    Toast.makeText(requireContext(), getString(R.string.success_player_added, playerName), Toast.LENGTH_SHORT).show()
                    loadPlayersData()
                }.addOnFailureListener {
                    if (_binding == null) return@addOnFailureListener
                    Toast.makeText(requireContext(), getString(R.string.error_adding_player), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showDeletePlayerConfirmationDialog(player: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.dialog_title_delete_player))
            .setMessage(getString(R.string.dialog_message_delete_player, player))
            .setPositiveButton(getString(R.string.action_delete)) { _, _ ->
                deletePlayer(player)
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    private fun deletePlayer(playerName: String) {
        user?.email?.let { email ->
            activeSport?.let { sport ->
                val sportDocRef = db.collection("users").document(email)
                    .collection("Deportes").document(sport)

                db.runBatch { batch ->
                    batch.update(sportDocRef, "jugadores", FieldValue.arrayRemove(playerName))
                    batch.update(sportDocRef, "ranking.$playerName", FieldValue.delete())
                }.addOnSuccessListener { 
                    if (_binding == null) return@addOnSuccessListener
                    Toast.makeText(requireContext(), getString(R.string.success_player_deleted, playerName), Toast.LENGTH_SHORT).show()
                    loadPlayersData()
                }.addOnFailureListener {
                    if (_binding == null) return@addOnFailureListener
                    Toast.makeText(requireContext(), getString(R.string.error_deleting_player), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showEmptyState(message: String) {
        binding.playersRecyclerview.visibility = View.GONE
        binding.emptyPlayersMessage.visibility = View.VISIBLE
        binding.emptyPlayersMessage.text = message
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        loadingDialog?.dismiss()
    }
}

// --- RecyclerView Adapter ---
class PlayersAdapter(
    private var players: List<String>,
    private val onEditClicked: (String) -> Unit,
    private val onDeleteClicked: (String) -> Unit
) : RecyclerView.Adapter<PlayersAdapter.PlayerViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlayerViewHolder {
        val binding = ItemPlayerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PlayerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PlayerViewHolder, position: Int) {
        holder.bind(players[position])
    }

    override fun getItemCount(): Int = players.size

    fun updateData(newPlayers: List<String>) {
        players = newPlayers
        notifyDataSetChanged()
    }

    inner class PlayerViewHolder(private val binding: ItemPlayerBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(playerName: String) {
            binding.playerNameTextview.text = playerName
            binding.editPlayerButton.setOnClickListener { onEditClicked(playerName) }
            binding.deletePlayerButton.setOnClickListener { onDeleteClicked(playerName) }
        }
    }
}