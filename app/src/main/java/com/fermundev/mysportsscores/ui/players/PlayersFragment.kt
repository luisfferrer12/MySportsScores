package com.fermundev.mysportsscores.ui.players

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
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

class PlayersFragment : Fragment() {

    private var _binding: FragmentPlayersBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()
    private val user = FirebaseAuth.getInstance().currentUser
    private lateinit var playersAdapter: PlayersAdapter
    private var activeSport: String? = null

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
        playersAdapter = PlayersAdapter(emptyList()) { player ->
            showDeletePlayerConfirmationDialog(player)
        }
        binding.playersRecyclerview.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = playersAdapter
        }
    }

    private fun loadPlayersData() {
        user?.email?.let { email ->
            db.collection("users").document(email).get().addOnSuccessListener { userDoc ->
                if (_binding == null) return@addOnSuccessListener // Safety check
                activeSport = userDoc.getString("grupoActivo")
                if (activeSport.isNullOrEmpty()) {
                    showEmptyState("No has seleccionado un deporte activo.")
                    binding.playersTitle.text = "Jugadores"
                    return@addOnSuccessListener
                }

                binding.playersTitle.text = "Jugadores de $activeSport"
                val sportDocRef = db.collection("users").document(email)
                    .collection("Deportes").document(activeSport!!)

                sportDocRef.get().addOnSuccessListener { sportSnapshot ->
                    if (_binding == null) return@addOnSuccessListener // Safety check
                    val players = sportSnapshot.get("jugadores") as? List<String> ?: emptyList()
                    if (players.isEmpty()) {
                        showEmptyState("No hay jugadores en este deporte. ¡Añade uno!")
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
            Toast.makeText(context, "Selecciona un deporte activo primero", Toast.LENGTH_SHORT).show()
            return
        }

        val editText = EditText(context)
        editText.hint = "Nombre del jugador"

        AlertDialog.Builder(requireContext())
            .setTitle("Añadir Nuevo Jugador")
            .setView(editText)
            .setPositiveButton("Añadir") { _, _ ->
                val newPlayerName = editText.text.toString().trim()
                if (newPlayerName.isNotEmpty()) {
                    addPlayerToSport(newPlayerName)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun addPlayerToSport(playerName: String) {
        val sportDocRef = db.collection("users").document(user!!.email!!)
            .collection("Deportes").document(activeSport!!)

        db.runBatch { batch ->
            batch.update(sportDocRef, "jugadores", FieldValue.arrayUnion(playerName))
            batch.update(sportDocRef, "ranking.$playerName", 0)
        }.addOnSuccessListener { 
            if (_binding == null) return@addOnSuccessListener
            Toast.makeText(context, "Jugador '$playerName' añadido", Toast.LENGTH_SHORT).show()
            loadPlayersData() // Recargar
        }.addOnFailureListener {
            if (_binding == null) return@addOnFailureListener
            Toast.makeText(context, "Error al añadir jugador", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDeletePlayerConfirmationDialog(player: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Eliminar Jugador")
            .setMessage("¿Seguro que deseas eliminar a '$player'? Esta acción también lo eliminará del ranking. Los resultados de partidos existentes no se verán afectados.")
            .setPositiveButton("Eliminar") { _, _ ->
                deletePlayer(player)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun deletePlayer(playerName: String) {
        val sportDocRef = db.collection("users").document(user!!.email!!)
            .collection("Deportes").document(activeSport!!)

        db.runBatch { batch ->
            batch.update(sportDocRef, "jugadores", FieldValue.arrayRemove(playerName))
            batch.update(sportDocRef, "ranking.$playerName", FieldValue.delete())
        }.addOnSuccessListener { 
            if (_binding == null) return@addOnSuccessListener
            Toast.makeText(context, "Jugador '$playerName' eliminado", Toast.LENGTH_SHORT).show()
            loadPlayersData() // Recargar
        }.addOnFailureListener {
            if (_binding == null) return@addOnFailureListener
            Toast.makeText(context, "Error al eliminar jugador", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showEmptyState(message: String) {
        binding.playersRecyclerview.visibility = View.GONE
        binding.emptyPlayersMessage.visibility = View.VISIBLE
        binding.emptyPlayersMessage.text = message
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// --- RecyclerView Adapter ---
class PlayersAdapter(
    private var players: List<String>,
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
            binding.deletePlayerButton.setOnClickListener { onDeleteClicked(playerName) }
        }
    }
}