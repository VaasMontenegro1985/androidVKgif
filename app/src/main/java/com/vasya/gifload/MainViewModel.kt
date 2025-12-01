package com.vasya.gifload

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap

class MainViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _images = MutableStateFlow(emptyList<ImageItem>())
    val images: StateFlow<List<ImageItem>> = _images.asStateFlow()

    private var currentPage = 0
    private var canLoadMore = true
    private var isLoading = false

    // Кэш для хранения загруженных данных
    private val memoryCache = ConcurrentHashMap<Int, List<ImageItem>>()

    init {
        loadInitialData()
    }

    fun loadInitialData() {
        if (isLoading) return

        viewModelScope.launch {
            isLoading = true
            currentPage = 0
            canLoadMore = true

            try {
                _uiState.value = UiState.Loading

                val response = giphyApi.getTrendingGifs(
                    limit = 20,
                    offset = currentPage * 20
                )

                val newImages = response.data.map { gif ->
                    ImageItem(
                        id = gif.id,
                        url = gif.images.fixed_height.url,
                        width = gif.images.fixed_height.width.toIntOrNull() ?: 200,
                        height = gif.images.fixed_height.height.toIntOrNull() ?: 200
                    )
                }

                memoryCache[currentPage] = newImages
                _images.value = newImages
                currentPage++
                _uiState.value = UiState.Success(_images.value)

            } catch (e: Exception) {
                _uiState.value = UiState.Error("Ошибка загрузки: ${e.message ?: "Неизвестная ошибка"}")
            } finally {
                isLoading = false
            }
        }
    }

    fun loadMoreData() {
        if (isLoading || !canLoadMore) return

        viewModelScope.launch {
            isLoading = true

            try {
                _uiState.value = UiState.PaginationLoading

                // Проверяем кэш
                val cachedData = memoryCache[currentPage]
                if (cachedData != null) {
                    _images.value = _images.value + cachedData
                    currentPage++
                    _uiState.value = UiState.Success(_images.value)
                    return@launch
                }

                val response = giphyApi.getTrendingGifs(
                    limit = 20,
                    offset = currentPage * 20
                )

                if (response.data.isEmpty()) {
                    canLoadMore = false
                    return@launch
                }

                val newImages = response.data.map { gif ->
                    ImageItem(
                        id = gif.id,
                        url = gif.images.fixed_height.url,
                        width = gif.images.fixed_height.width.toIntOrNull() ?: 200,
                        height = gif.images.fixed_height.height.toIntOrNull() ?: 200
                    )
                }

                memoryCache[currentPage] = newImages
                _images.value = _images.value + newImages
                currentPage++
                _uiState.value = UiState.Success(_images.value)

            } catch (e: Exception) {
                _uiState.value = UiState.Error("Ошибка загрузки: ${e.message ?: "Неизвестная ошибка"}")
            } finally {
                isLoading = false
            }
        }
    }

    fun retry() {
        loadInitialData()
    }

    fun getImageIndex(imageId: String): Int {
        return _images.value.indexOfFirst { it.id == imageId } + 1
    }
}