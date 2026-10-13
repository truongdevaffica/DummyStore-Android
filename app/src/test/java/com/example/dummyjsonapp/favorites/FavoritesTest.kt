package com.example.dummyjsonapp.favorites

import com.example.dummyjsonapp.data.local.dao.ProductDao
import com.example.dummyjsonapp.data.local.entity.ProductEntity
import com.example.dummyjsonapp.data.remote.ApiService
import com.example.dummyjsonapp.data.repository.ProductRepositoryImpl
import com.example.dummyjsonapp.domain.model.ProductModel
import com.example.dummyjsonapp.domain.repository.ProductRepository
import com.example.dummyjsonapp.domain.result.Resource
import com.example.dummyjsonapp.presentation.favotite.FavoriteViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesTest {
    private val dispatcher = StandardTestDispatcher()
    private val dao = mockk<ProductDao>()
    private val repository = ProductRepositoryImpl(mockk<ApiService>(), dao)
    private val entity = ProductEntity(1, "Phone", "Description", 10.0, 4.0, "", 0.0, "phones", 1, emptyList())
    private val product = ProductModel(1, "Phone", "Description", 10.0, 4.0, "", 0.0, "phones", 1, emptyList(), isFavorite = true)

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `empty favorites emits loading then empty success`() = runTest {
        coEvery { dao.getFavoriteProducts() } returns emptyList()
        val emissions = repository.getFavoriteProducts().toList()
        assertEquals(2, emissions.size)
        assertEquals(Resource.Loading, emissions.first())
        assertTrue((emissions.last() as Resource.Success<List<ProductModel>>).data.isEmpty())
    }

    @Test fun `favorites emits mapped products marked favorite`() = runTest {
        coEvery { dao.getFavoriteProducts() } returns listOf(entity)
        val emissions = repository.getFavoriteProducts().toList()
        assertEquals(2, emissions.size)
        assertEquals(Resource.Loading, emissions.first())
        assertEquals(listOf(product), (emissions.last() as Resource.Success<List<ProductModel>>).data)
    }

    @Test fun `removing final favorite emits empty success`() = runTest {
        var favorites = listOf(entity)
        coEvery { dao.getFavoriteProducts() } answers { favorites }
        coEvery { dao.removeFavorite(1) } answers { favorites = emptyList() }
        assertEquals(1, (repository.getFavoriteProducts().toList().last() as Resource.Success<List<ProductModel>>).data.size)
        repository.toggleFavorite(1, false)
        val emissions = repository.getFavoriteProducts().toList()
        assertEquals(Resource.Loading, emissions.first())
        assertTrue((emissions.last() as Resource.Success<List<ProductModel>>).data.isEmpty())
        coVerify(exactly = 1) { dao.removeFavorite(1) }
    }

    @Test fun `database failure emits error`() = runTest {
        coEvery { dao.getFavoriteProducts() } throws IllegalStateException("Database failed")
        val emissions = repository.getFavoriteProducts().toList()
        assertEquals(Resource.Loading, emissions.first())
        assertEquals("Database failed", (emissions.last() as Resource.Error).message)
    }

    @Test fun `cancellation propagates`() = runTest {
        coEvery { dao.getFavoriteProducts() } throws CancellationException("Cancelled")
        try {
            repository.getFavoriteProducts().toList()
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
    }

    @Test fun `viewmodel finishes loading for empty favorites`() = runTest(dispatcher) {
        val source = mockk<ProductRepository>()
        every { source.getFavoriteProducts() } returns flowOf(Resource.Loading, Resource.Success(emptyList()))
        val viewModel = FavoriteViewModel(source)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.products.isEmpty())
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test fun `viewmodel removes last product and finishes loading`() = runTest(dispatcher) {
        val source = mockk<ProductRepository>()
        var favorites = listOf(product)
        every { source.getFavoriteProducts() } answers { flowOf(Resource.Loading, Resource.Success(favorites)) }
        coEvery { source.toggleFavorite(1, false) } answers { favorites = emptyList() }
        val viewModel = FavoriteViewModel(source)
        advanceUntilIdle()
        assertEquals(listOf(product), viewModel.uiState.value.products)
        viewModel.toggleFavorite(1, false)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.products.isEmpty())
        assertNull(viewModel.uiState.value.errorMessage)
        coVerify(exactly = 1) { source.toggleFavorite(1, false) }
    }

    @Test fun `successful retry clears old error`() = runTest(dispatcher) {
        val source = mockk<ProductRepository>()
        every { source.getFavoriteProducts() } returns flowOf(Resource.Loading, Resource.Error("Database failed"))
        val viewModel = FavoriteViewModel(source)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("Database failed", viewModel.uiState.value.errorMessage)
        every { source.getFavoriteProducts() } returns flowOf(Resource.Loading, Resource.Success(emptyList()))
        viewModel.loadFavoriteProductsList()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.products.isEmpty())
    }

    @Test fun `failed removal ends loading and retains product`() = runTest(dispatcher) {
        val source = mockk<ProductRepository>()
        every { source.getFavoriteProducts() } returns flowOf(Resource.Loading, Resource.Success(listOf(product)))
        coEvery { source.toggleFavorite(1, false) } throws IllegalStateException("Write failed")
        val viewModel = FavoriteViewModel(source)
        advanceUntilIdle()
        viewModel.toggleFavorite(1, false)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("Write failed", viewModel.uiState.value.errorMessage)
        assertEquals(listOf(product), viewModel.uiState.value.products)
    }
}
