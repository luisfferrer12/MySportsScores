package com.fermundev.mysportsscores.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fermundev.mysportsscores.R
import com.fermundev.mysportsscores.SportType
import com.fermundev.mysportsscores.databinding.FragmentHomeBinding
import com.fermundev.mysportsscores.databinding.ItemRankingBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

// Sealed class to represent the different types of ranking data for the adapter
sealed class RankingItem {
    abstract val name: String
    data class Opposition(override val name: String, val score: Long) : RankingItem()
    data class Performance(override val name: String, val bestScore: Double) : RankingItem()
}

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val db = FirebaseFirestore.getInstance()
    private val user = FirebaseAuth.getInstance().currentUser
    private lateinit var rankingAdapter: RankingAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        setupRecyclerView()
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        loadActiveGroupData()
    }

    private fun setupRecyclerView() {
        rankingAdapter = RankingAdapter(emptyList())
        binding.rankingRecyclerview.layoutManager = LinearLayoutManager(requireContext())
        binding.rankingRecyclerview.adapter = rankingAdapter
    }

    private fun loadActiveGroupData() {
        user?.email?.let { email ->
            db.collection("users").document(email).get()
                .addOnSuccessListener { userDoc ->
                    if (_binding == null) return@addOnSuccessListener
                    val activeSport = userDoc.getString("grupoActivo")

                    if (activeSport.isNullOrEmpty()) {
                        showEmptyState(getString(R.string.empty_state_no_sport))
                        binding.mainContentGroup.visibility = View.GONE
                    } else {
                        binding.sportTitleTextview.text = activeSport
                        // Fetch the sport document to decide which ranking to load
                        db.collection("users").document(email).collection("Deportes").document(activeSport).get()
                            .addOnSuccessListener { sportDoc ->
                                if (_binding == null) return@addOnSuccessListener
                                val sportType = sportDoc.getString("sportType")?.let { SportType.valueOf(it) } ?: SportType.OPPOSITION

                                binding.emptyStateMessage.visibility = View.GONE
                                binding.mainContentGroup.visibility = View.VISIBLE

                                when (sportType) {
                                    SportType.PERFORMANCE -> loadPerformanceRanking(sportDoc)
                                    SportType.OPPOSITION -> loadOppositionRanking(sportDoc)
                                }
                            }
                            .addOnFailureListener { showEmptyState(getString(R.string.error_connection_home)) }
                    }
                }
                .addOnFailureListener { showEmptyState(getString(R.string.error_connection_home)) }
        }
    }

    private fun loadOppositionRanking(sportDocument: DocumentSnapshot) {
        val rankingMap = sportDocument.get("ranking") as? Map<String, Long> ?: emptyMap()

        if (rankingMap.isEmpty()) {
            showEmptyRankingState()
        } else {
            val sortedRanking = rankingMap.toList()
                .sortedByDescending { (_, points) -> points }
                .map { RankingItem.Opposition(it.first, it.second) }
            
            binding.rankingRecyclerview.visibility = View.VISIBLE
            binding.emptyRankingMessage.visibility = View.GONE
            
            updatePodium(sortedRanking)
            rankingAdapter.updateData(sortedRanking)
        }
    }

    private fun loadPerformanceRanking(sportDocument: DocumentSnapshot) {
        sportDocument.reference.collection("Resultados").get()
            .addOnSuccessListener { resultsSnapshot ->
                if (_binding == null) return@addOnSuccessListener
                
                if (resultsSnapshot.isEmpty) {
                    showEmptyRankingState()
                    return@addOnSuccessListener
                }

                val bestScores = resultsSnapshot.documents
                    .filter { it.getString("type") == "PERFORMANCE" && it.contains("player") && it.contains("result") }
                    .groupBy { it.getString("player")!! }
                    .mapValues { entry -> 
                        entry.value.minOf { it.getDouble("result")!! }
                    }
                
                if (bestScores.isEmpty()) {
                     showEmptyRankingState()
                } else {
                    val sortedRanking = bestScores.toList()
                        .sortedBy { it.second } // Sort by score, lower is better
                        .map { RankingItem.Performance(it.first, it.second) }

                    binding.rankingRecyclerview.visibility = View.VISIBLE
                    binding.emptyRankingMessage.visibility = View.GONE

                    updatePodium(sortedRanking)
                    rankingAdapter.updateData(sortedRanking)
                }
            }
    }

    private fun showEmptyState(message: String) {
        binding.emptyStateMessage.text = message
        binding.emptyStateMessage.visibility = View.VISIBLE
        binding.mainContentGroup.visibility = View.GONE
    }

    private fun showEmptyRankingState() {
        binding.emptyRankingMessage.text = getString(R.string.empty_state_no_ranking)
        binding.emptyRankingMessage.visibility = View.VISIBLE
        binding.podiumGroup.visibility = View.INVISIBLE
        binding.rankingRecyclerview.visibility = View.GONE
    }

    private fun updatePodium(sortedRanking: List<RankingItem>) {
        binding.podiumGroup.visibility = if (sortedRanking.isEmpty()) View.INVISIBLE else View.VISIBLE
        val placeholder = getString(R.string.placeholder_podium)
        binding.firstPlaceName.text = placeholder
        binding.secondPlaceName.text = placeholder
        binding.thirdPlaceName.text = placeholder

        if (sortedRanking.isNotEmpty()) {
            binding.firstPlaceName.text = sortedRanking[0].name
        }
        if (sortedRanking.size >= 2) {
            binding.secondPlaceName.text = sortedRanking[1].name
        }
        if (sortedRanking.size >= 3) {
            binding.thirdPlaceName.text = sortedRanking[2].name
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // --- RecyclerView Adapter ---
    inner class RankingAdapter(private var items: List<RankingItem>) : RecyclerView.Adapter<RankingAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemRankingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position], position)
        }

        override fun getItemCount(): Int = items.size
        
        fun updateData(newItems: List<RankingItem>){
            items = newItems
            notifyDataSetChanged()
        }

        inner class ViewHolder(private val binding: ItemRankingBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(item: RankingItem, position: Int) {
                binding.positionTextview.text = getString(R.string.ranking_position, position + 1)
                binding.playerNameTextview.text = item.name

                // Display score based on item type
                when(item) {
                    is RankingItem.Opposition -> {
                        binding.pointsTextview.text = getString(R.string.ranking_points, item.score)
                    }
                    is RankingItem.Performance -> {
                        binding.pointsTextview.text = String.format(Locale.getDefault(), "%.2f", item.bestScore)
                    }
                }

                when (position) {
                    0 -> {
                        binding.medalImageview.visibility = View.VISIBLE
                        binding.medalImageview.setImageResource(R.drawable.ic_gold_medal)
                    }
                    1 -> {
                        binding.medalImageview.visibility = View.VISIBLE
                        binding.medalImageview.setImageResource(R.drawable.ic_silver_medal)
                    }
                    2 -> {
                        binding.medalImageview.visibility = View.VISIBLE
                        binding.medalImageview.setImageResource(R.drawable.ic_bronze_medal)
                    }
                    else -> {
                        binding.medalImageview.visibility = View.GONE
                    }
                }
            }
        }
    }
}