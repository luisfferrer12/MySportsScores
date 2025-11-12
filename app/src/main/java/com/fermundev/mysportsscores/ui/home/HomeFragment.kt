package com.fermundev.mysportsscores.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fermundev.mysportsscores.R
import com.fermundev.mysportsscores.databinding.FragmentHomeBinding
import com.fermundev.mysportsscores.databinding.ItemRankingBinding
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
        user?.email?.let { email ->
            db.collection("users").document(email).get()
                .addOnSuccessListener { document ->
                    if (_binding == null) return@addOnSuccessListener // Safety check
                    if (document != null && document.exists()) {
                        val activeGroup = document.getString("grupoActivo")

                        if (activeGroup.isNullOrEmpty()) {
                            binding.emptyStateMessage.visibility = View.VISIBLE
                            binding.mainContentGroup.visibility = View.GONE
                        } else {
                            binding.emptyStateMessage.visibility = View.GONE
                            binding.mainContentGroup.visibility = View.VISIBLE
                            binding.sportTitleTextview.text = activeGroup
                            loadRankingData(email, activeGroup)
                        }
                    } else {
                        binding.emptyStateMessage.visibility = View.VISIBLE
                        binding.mainContentGroup.visibility = View.GONE
                    }
                }
                .addOnFailureListener { 
                    if (_binding == null) return@addOnFailureListener
                    binding.emptyStateMessage.text = "Error de conexión"
                    binding.emptyStateMessage.visibility = View.VISIBLE
                    binding.mainContentGroup.visibility = View.GONE
                 }
        }
    }

    private fun loadRankingData(email: String, sportName: String) {
        val sportDocRef = db.collection("users").document(email)
            .collection("Deportes").document(sportName)

        sportDocRef.get().addOnSuccessListener { sportDocument ->
            if (_binding == null) return@addOnSuccessListener // Safety check
            val rankingMap = sportDocument.get("ranking") as? Map<String, Long> ?: emptyMap()

            if (rankingMap.isEmpty()) {
                binding.rankingRecyclerview.visibility = View.GONE
                binding.emptyRankingMessage.visibility = View.VISIBLE
                updatePodium(emptyList())
            } else {
                val sortedRanking = rankingMap.toList().sortedByDescending { (_, points) -> points }
                
                binding.rankingRecyclerview.visibility = View.VISIBLE
                binding.emptyRankingMessage.visibility = View.GONE
                
                updatePodium(sortedRanking)
                setupRecyclerView(sortedRanking)
            }
        }
    }

    private fun updatePodium(sortedRanking: List<Pair<String, Long>>) {
        // No need for safety check here, as it's called synchronously
        binding.firstPlaceName.text = "-"
        binding.secondPlaceName.text = "-"
        binding.thirdPlaceName.text = "-"

        if (sortedRanking.isNotEmpty()) {
            binding.firstPlaceName.text = sortedRanking[0].first
        }
        if (sortedRanking.size >= 2) {
            binding.secondPlaceName.text = sortedRanking[1].first
        }
        if (sortedRanking.size >= 3) {
            binding.thirdPlaceName.text = sortedRanking[2].first
        }
    }

    private fun setupRecyclerView(rankingList: List<Pair<String, Long>>) {
        // No need for safety check here
        val adapter = RankingAdapter(rankingList)
        binding.rankingRecyclerview.layoutManager = LinearLayoutManager(requireContext())
        binding.rankingRecyclerview.adapter = adapter
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // --- RecyclerView Adapter ---
    inner class RankingAdapter(private val items: List<Pair<String, Long>>) : RecyclerView.Adapter<RankingAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemRankingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position], position)
        }

        override fun getItemCount(): Int = items.size

        inner class ViewHolder(private val binding: ItemRankingBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(item: Pair<String, Long>, position: Int) {
                val (name, points) = item
                binding.positionTextview.text = "${position + 1}."
                binding.playerNameTextview.text = name
                binding.pointsTextview.text = "$points pts"

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
