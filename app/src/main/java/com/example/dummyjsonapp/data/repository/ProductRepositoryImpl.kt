package com.example.dummyjsonapp.data.repository

import com.example.dummyjsonapp.data.remote.ApiService
import com.example.dummyjsonapp.data.local.dao.ProductDao
import com.example.dummyjsonapp.data.local.entity.CartEntity
import com.example.dummyjsonapp.data.local.entity.FavoriteEntity
import com.example.dummyjsonapp.data.mapper.toEntity
import com.example.dummyjsonapp.data.mapper.toDomain
import com.example.dummyjsonapp.domain.model.CartItemModel
import com.example.dummyjsonapp.domain.model.CategoryModel
import com.example.dummyjsonapp.domain.repository.ProductRepository
import com.example.dummyjsonapp.domain.model.ProductModel
import com.example.dummyjsonapp.domain.result.Resource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ProductRepositoryImpl @Inject constructor(
    private val apiService: ApiService,
    private val productDao : ProductDao
): ProductRepository {

    override fun getProducts(): Flow<Resource<List<ProductModel>>> = flow{

        emit(Resource.Loading)
        val localProducts = productDao.getAllProducts().map { it.toDomain() }

        if(localProducts.isNotEmpty()){
            emit(Resource.Success(localProducts))
        }
        try {
            val response = apiService.getProducts()

            if(response.isSuccessful && response.body() != null){
                val netProducts = response.body()!!.products.map { it.toEntity() }
                productDao.insertProducts(netProducts)

                val newProducts = productDao.getAllProducts().map { it.toDomain() }
                emit(Resource.Success(newProducts))

            }else{
                throw Exception("Lỗi API: ${response.code()}")
            }
        }catch (e: CancellationException){
            throw e

        }catch (e: Exception){
            if(localProducts.isEmpty()){
                emit(Resource.Error(e.message ?: "Không thể tải dữ liệu!"))
            }else{
                emit(Resource.Success(localProducts))
            }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun getProductById(id: Int): ProductModel? {
        return productDao.getProductById(id)?.toDomain()
    }

    override fun searchProducts(keyword: String): Flow<Resource<List<ProductModel>>> = flow {
        emit(Resource.Loading)

        val localData = productDao.searchProducts(keyword).map { it.toDomain() }
        if (localData.isNotEmpty()) {
            emit(Resource.Success(localData))
        }

        try {
            val response = apiService.searchProducts(keyword)
            if (response.isSuccessful && response.body() != null) {
                val entities = response.body()!!.products.map { it.toEntity() }
                productDao.insertProducts(entities)

                val newData = productDao.searchProducts(keyword).map { it.toDomain() }
                emit(Resource.Success(newData))
            } else {
                throw Exception("Lỗi API: ${response.code()}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (localData.isEmpty()) {
                emit(Resource.Error("Bạn đang offline và không có dữ liệu cũ!"))
            } else {
                emit(Resource.Success(localData))
            }
        }
    }.flowOn(Dispatchers.IO)

    override fun getCategories(): Flow<Resource<List<CategoryModel>>> = flow{
        val localCategories = productDao.getAllCategories().map { it.toDomain() }
        if (localCategories.isNotEmpty()) {
            emit(Resource.Success(localCategories))
        }
        try {
            val response = apiService.getCategories()
            if (response.isSuccessful && response.body() != null) {
                val netCategories = response.body()!!.map { it.toEntity() }
                productDao.insertCategories(netCategories)

                val newCategories = productDao.getAllCategories().map { it.toDomain() }
                emit(Resource.Success(newCategories))
            } else {
                throw Exception("Lỗi API: ${response.code()}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (localCategories.isEmpty()) {
                emit(Resource.Error(e.message ?: "Không thể tải danh mục!"))
            } else {
                emit(Resource.Success(localCategories))
            }
        }
    }.flowOn(Dispatchers.IO)

    override fun getProductsByCategory(categorySlug: String): Flow<Resource<List<ProductModel>>> = flow {
        emit(Resource.Loading)

        val localData = productDao.getProductsByCategory(categorySlug).map { it.toDomain() }
        if (localData.isNotEmpty()) {
            emit(Resource.Success(localData))
        }

        try {
            val response = apiService.getProductsByCategory(categorySlug)
            if (response.isSuccessful && response.body() != null) {
                val entities = response.body()!!.products.map { it.toEntity() }
                productDao.insertProducts(entities)

                val newData = productDao.getProductsByCategory(categorySlug).map { it.toDomain() }
                emit(Resource.Success(newData))
            } else {
                throw Exception("Lỗi API: ${response.code()}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (localData.isEmpty()) {
                emit(Resource.Error("Bạn đang offline!"))
            } else {
                emit(Resource.Success(localData))
            }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun toggleFavorite(productId: Int, isFavorite: Boolean) {
        if (isFavorite) {
            productDao.insertFavorite(FavoriteEntity(productId))
        } else {
            productDao.removeFavorite(productId)
        }
    }

    override suspend fun getAllFavoriteIds(): List<Int> {
        return productDao.getAllFavoriteIds()
    }
    override fun getFavoriteProducts(): Flow<Resource<List<ProductModel>>> = flow {
        emit(Resource.Loading)
        try {
            val favorites = productDao.getFavoriteProducts().map { it.toDomain().copy(isFavorite = true) }
            emit(Resource.Success(favorites))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(Resource.Error(e.message ?: "Không thể tải danh sách yêu thích!"))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun addToCart(productId: Int) : Boolean {
        val product = productDao.getProductById(productId) ?: return false
        val existingItem = productDao.getCartItemById(productId)

        val currentQuantity = existingItem?.quantity ?: 0
        if(currentQuantity + 1> product.stock){
            return false
        }
        if (existingItem != null) {
            existingItem.quantity += 1
            productDao.insertOrUpdateCart(existingItem)
        } else {
            productDao.insertOrUpdateCart(CartEntity(productId, 1))
        }
        return true
    }

    override suspend fun getCartItems(): List<CartItemModel> {
        return productDao.getCartItems().map { it.toDomain() }
    }

    override suspend fun updateCartQuantity(productId: Int, quantity: Int): Boolean {
        val product = productDao.getProductById(productId) ?: return false
        if(quantity > product.stock) return false
        if (quantity > 0) {
            val item = productDao.getCartItemById(productId)
            if (item != null) {
                item.quantity = quantity
                productDao.insertOrUpdateCart(item)
            }
        } else {
            // Nếu số lượng tụt xuống 0 thì xóa luôn khỏi giỏ
            productDao.removeFromCart(productId)
        }
        return true
    }

    override suspend fun removeFromCart(productId: Int) {
        productDao.removeFromCart(productId)
    }
    override suspend fun clearCart() {
        productDao.clearCart()
    }
}