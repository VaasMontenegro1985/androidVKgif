package com.vasya.gifload

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
}

// Модели данных
data class GiphyResponse(val data: List<GifData>)
data class GifData(val id: String, val images: Images)
data class Images(val fixed_height: ImageInfo)
data class ImageInfo(val url: String, val height: String, val width: String)
data class ImageItem(val id: String, val url: String, val width: Int, val height: Int)

// Состояния UI
sealed class UiState {
    object Loading : UiState()
    data class Success(val images: List<ImageItem>) : UiState()
    data class Error(val message: String) : UiState()
    object PaginationLoading : UiState()
}

// API сервис
interface GiphyApiService {
    @GET("gifs/trending")
    suspend fun getTrendingGifs(
        @Query("api_key") apiKey: String = "QKqhxhLRNL6KZDT9HfnDXFoN9n0ClpyY", // Апи ключ Giphy
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): GiphyResponse
}

// Создаем Retrofit
val giphyApi: GiphyApiService by lazy {
    Retrofit.Builder()
        .baseUrl("https://api.giphy.com/v1/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(GiphyApiService::class.java)
}

// Главный экран
@Composable
fun MainScreen() {
    var uiState by remember { mutableStateOf<UiState>(UiState.Loading) }
    var images by remember { mutableStateOf(emptyList<ImageItem>()) }
    var page by remember { mutableStateOf(0) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Функция загрузки данных
    fun loadGifs(isPagination: Boolean = false) {
        scope.launch(Dispatchers.IO) {
            try {
                if (isPagination) {
                    uiState = UiState.PaginationLoading
                }

                val response = giphyApi.getTrendingGifs(
                    limit = 20,
                    offset = page * 20
                )

                val newImages = response.data.map { gif ->
                    ImageItem(
                        id = gif.id,
                        url = gif.images.fixed_height.url,
                        width = gif.images.fixed_height.width.toIntOrNull() ?: 200,
                        height = gif.images.fixed_height.height.toIntOrNull() ?: 200
                    )
                }

                if (isPagination) {
                    images = images + newImages
                    page++
                } else {
                    images = newImages
                    page = 1
                }

                uiState = UiState.Success(images)

            } catch (e: Exception) {
                uiState = UiState.Error("Ошибка: ${e.message ?: "Неизвестная ошибка"}")
            }
        }
    }

    // Первая загрузка
    LaunchedEffect(Unit) {
        loadGifs()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (uiState) {
                is UiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    }
                }

                is UiState.Error -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = (uiState as UiState.Error).message,
                            modifier = Modifier.padding(16.dp)
                        )
                        Button(onClick = { loadGifs() }) {
                            Text("Повторить")
                        }
                    }
                }

                is UiState.Success -> {
                    val imageList = (uiState as UiState.Success).images

                    // Pinterest сетка 2 колонки
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(imageList) { index, image ->
                            GifCard(
                                image = image,
                                index = index,
                                onClick = {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            "GIF №${index + 1}"
                                        )
                                    }
                                }
                            )
                        }

                        // Кнопка для подгрузки (вместо автоскролла пока что)
                        item {
                            Button(
                                onClick = { loadGifs(true) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Text("Загрузить ещё")
                            }
                        }
                    }
                }

                is UiState.PaginationLoading -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Показываем существующие изображения
                        if (images.isNotEmpty()) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                itemsIndexed(images) { index, image ->
                                    GifCard(
                                        image = image,
                                        index = index,
                                        onClick = {}
                                    )
                                }
                            }
                        }

                        // Индикатор загрузки поверх
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(48.dp))
                        }
                    }
                }
            }
        }
    }
}

// Компонент карточки
@Composable
fun GifCard(
    image: ImageItem,
    index: Int,
    onClick: () -> Unit
) {
    // Рассчитываем пропорции для Pinterest-стиля
    val aspectRatio = if (image.width > 0 && image.height > 0) {
        image.width.toFloat() / image.height.toFloat()
    } else {
        // Случайные пропорции если нет данных
        when (index % 5) {
            0 -> 0.7f
            1 -> 0.9f
            2 -> 1.1f
            3 -> 1.3f
            else -> 0.8f
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(3.dp)
    ) {
        Box {
            // GIF изображение
            Image(
                painter = rememberAsyncImagePainter(model = image.url),
                contentDescription = "Анимированный GIF",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio.coerceIn(0.6f, 1.4f)),
                contentScale = ContentScale.Crop
            )

            // Номер в левом верхнем углу
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "${index + 1}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}