package com.example.dummyjsonapp.presentation.favotite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dummyjsonapp.domain.repository.ProductRepository
import com.example.dummyjsonapp.domain.result.Resource
import com.example.dummyjsonapp.presentation.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FavoriteViewModel @Inject constructor(
    private val repository: ProductRepository
): ViewModel(){
    private val _uiState = MutableStateFlow(UiState(isLoading = true))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        loadFavoriteProductsList()
    }

    fun loadFavoriteProductsList() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            repository.getFavoriteProducts().collect { resource ->
                when (resource) {
                    is Resource.Loading -> {
                        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
                    }
                    is Resource.Success -> {
                        _uiState.update { it.copy(isLoading = false, products = resource.data, errorMessage = null) }
                    }
                    is Resource.Error -> {
                        _uiState.update { it.copy(isLoading = false, errorMessage = resource.message) }
                    }
                }
            }
        }
    }
    fun toggleFavorite(productId: Int, isFavorite: Boolean) {
        viewModelScope.launch {
            loadJob?.cancel()
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                repository.toggleFavorite(productId, isFavorite)
                loadFavoriteProductsList()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = e.message ?: "Không thể cập nhật yêu thích!")
                }
            }
        }
    }
}