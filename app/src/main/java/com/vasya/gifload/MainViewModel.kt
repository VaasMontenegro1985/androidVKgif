package com.vasya.gifload

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

sealed class UiState {
    object Loading : UiState()
    data class Success(val images: List<ImageItem>) : UiState()
    data class Error(val message: String) : UiState()
    data class Paginating(val images: List<ImageItem>) : UiState()
}


class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState

    private var currentPage = 0
    private var isLoadingMore = false

    // Получаем API ключ из ресурсов приложения
    private val apiKey: String = application.getString(R.string.giphy_api_key)

    init {
        loadInitialData()
    }

    fun loadInitialData() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = UiState.Loading
            try {
                currentPage = 0
                val response = giphyApi.getTrendingGifs(
                    apiKey = apiKey,
                    limit = getApplication<Application>().resources.getInteger(R.integer.pagination_limit),
                    offset = 0
                )

                val newImages = response.data.map { gif ->
                    ImageItem(
                        id = gif.id,
                        url = gif.images.fixed_height.url,
                        width = gif.images.fixed_height.width.toIntOrNull() ?: 200,
                        height = gif.images.fixed_height.height.toIntOrNull() ?: 200
                    )
                }

                _uiState.value = UiState.Success(newImages)
                currentPage = 1
                isLoadingMore = false

            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Неизвестная ошибка")
                isLoadingMore = false
            }
        }
    }

    fun loadMoreData() {
        // Не загружаем, если уже грузится или нет данных
        if (isLoadingMore) return

        val currentData = _uiState.value
        if (currentData !is UiState.Success && currentData !is UiState.Paginating) return

        isLoadingMore = true
        val currentImages = when (currentData) {
            is UiState.Success -> currentData.images
            is UiState.Paginating -> currentData.images
            else -> emptyList()
        }

        // Показываем состояние "идёт загрузка"
        _uiState.value = UiState.Paginating(currentImages)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = giphyApi.getTrendingGifs(
                    apiKey = apiKey,
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

                val allImages = currentImages + newImages
                _uiState.value = UiState.Success(allImages)
                currentPage++
                isLoadingMore = false

            } catch (e: Exception) {
                val errorMessage = MyApp.instance.getString(
                    R.string.error_loading,
                    e.message ?: MyApp.instance.getString(R.string.error_loading)
                )
                _uiState.value = UiState.Error(errorMessage)
                isLoadingMore = false
            }
        }
    }
}