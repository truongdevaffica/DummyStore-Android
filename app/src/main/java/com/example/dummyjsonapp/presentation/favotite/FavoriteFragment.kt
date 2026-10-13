package com.example.dummyjsonapp.presentation.favotite

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.example.dummyjsonapp.databinding.FragmentFavoriteBinding
import com.example.dummyjsonapp.presentation.home.ProductAdapter
import com.example.dummyjsonapp.presentation.product.DetailActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FavoriteFragment : Fragment() {
    private var _binding: FragmentFavoriteBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: ProductAdapter
    private val viewModel: FavoriteViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        _binding = FragmentFavoriteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ProductAdapter(
            onItemClick = { clickedProduct ->
                val intent = Intent(requireContext(), DetailActivity::class.java)
                intent.putExtra("product_id", clickedProduct.id)
                startActivity(intent)
            },
            onFavoriteClick = { product, isFavorite ->
                viewModel.toggleFavorite(product.id, isFavorite)
            }
        )
        binding.rvFavorites.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvFavorites.adapter = adapter

        observeViewModel()
    }
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    adapter.submitList(state.products)
                    binding.progressFavorites.visibility = if (state.isLoading) View.VISIBLE else View.GONE
                    binding.tvFavoriteError.text = state.errorMessage
                    binding.tvFavoriteError.visibility =
                        if (!state.isLoading && state.errorMessage != null) View.VISIBLE else View.GONE
                    binding.layoutEmpty.visibility =
                        if (!state.isLoading && state.errorMessage == null && state.products.isEmpty())
                            View.VISIBLE else View.GONE
                    binding.rvFavorites.visibility =
                        if (!state.isLoading && state.errorMessage == null && state.products.isNotEmpty())
                            View.VISIBLE else View.GONE
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}