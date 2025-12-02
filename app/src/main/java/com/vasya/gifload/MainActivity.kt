package com.vasya.gifload

import android.os.Bundle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.rememberAsyncImagePainter
import coil.disk.DiskCache
import coil.memory.MemoryCache
import kotlinx.coroutines.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GIFLoaderApp()
        }
    }
}

// Модели

data class GiphyResponse(val data: List<GifData>)
data class GifData(val id: String, val images: Images)
data class Images(val fixed_height: ImageInfo)
data class ImageInfo(val url: String, val height: String, val width: String)
data class ImageItem(val id: String, val url: String, val width: Int, val height: Int)

// API сервисы

interface GiphyApiService {
    @GET("gifs/trending")
    suspend fun getTrendingGifs(
        @Query("api_key") apiKey: String,
        @Query("limit") limit: Int = MyApp.instance.resources.getInteger(R.integer.initial_load_limit),
        @Query("offset") offset: Int = 0
    ): GiphyResponse
}


@Composable
fun rememberCoilImageLoader(): ImageLoader {
    val context = LocalContext.current
    return remember {
        ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50L * 1024 * 1024) // 50 МБ
                    .build()
            }
            .okHttpClient {
                OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()
            }
            .build()
    }
}

// Retrofit
val giphyApi: GiphyApiService by lazy {
    val baseUrl = MyApp.instance.getString(R.string.giphy_api_base_url)

    Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(GiphyApiService::class.java)
}

// Главный экран

@Composable
fun MainScreen() {
    val viewModel: MainViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

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
                    LoadingView()
                }

                is UiState.Error -> {
                    ErrorView(
                        message = (uiState as UiState.Error).message,
                        onRetry = { viewModel.loadInitialData() }
                    )
                }

                is UiState.Success, is UiState.Paginating -> {
                    val images = when (uiState) {
                        is UiState.Success -> (uiState as UiState.Success).images
                        is UiState.Paginating -> (uiState as UiState.Paginating).images
                        else -> emptyList()
                    }
                    val isPaginating = uiState is UiState.Paginating

                    ImageGridScreen(
                        images = images,
                        isPaginating = isPaginating,
                        onLoadMore = { viewModel.loadMoreData() },
                        onImageClick = { index ->
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    context.getString(R.string.snackbar_gif_number, index + 1)
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

// UI COMPONENTS

@Composable
fun GIFLoaderApp() {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            MainScreen()
        }
    }
}

@Composable
fun LoadingView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(
                dimensionResource(id = R.dimen.progress_large)
            )
        )
    }
}

@Composable
fun ErrorView(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(dimensionResource(id = R.dimen.padding_large)),
            color = MaterialTheme.colorScheme.error
        )
        Button(onClick = onRetry) {
            Text(stringResource(R.string.retry_button))
        }
    }
}

@Composable
fun ImageGridScreen(
    images: List<ImageItem>,
    isPaginating: Boolean,
    onLoadMore: () -> Unit,
    onImageClick: (Int) -> Unit
) {
    val listState = rememberLazyStaggeredGridState()
    val imageLoader = rememberCoilImageLoader()

    // Бесконечный скролл
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisibleIndex ->
                if (lastVisibleIndex != null && lastVisibleIndex >= images.size - 3 && !isPaginating) {
                    onLoadMore()
                }
            }
    }

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(dimensionResource(id = R.dimen.grid_padding)),
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(id = R.dimen.grid_spacing)),
        verticalItemSpacing = dimensionResource(id = R.dimen.grid_spacing)
    ) {
        itemsIndexed(images) { index, image ->
            GifCard(
                image = image,
                index = index,
                imageLoader = imageLoader,
                onClick = { onImageClick(index) }
            )
        }

        if (isPaginating) {
            item(span = StaggeredGridItemSpan.FullLine) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(dimensionResource(id = R.dimen.padding_large)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(dimensionResource(id = R.dimen.padding_small))
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(
                                dimensionResource(id = R.dimen.progress_medium)
                            )
                        )
                        Text(
                            text = stringResource(R.string.loading_more),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GifCard(
    image: ImageItem,
    index: Int,
    imageLoader: ImageLoader,
    onClick: () -> Unit
) {
    // Используем реальные пропорции изображения
    val aspectRatio = if (image.width > 0 && image.height > 0) {
        image.width.toFloat() / image.height.toFloat()
    } else {
        // Случайные пропорции для разнообразия если нет данных
        when (index % 6) {
            0 -> 0.7f
            1 -> 0.9f
            2 -> 1.1f
            3 -> 1.3f
            4 -> 0.8f
            else -> 1.0f
        }
    }

    val minAspectRatio = MyApp.instance.resources.getFraction(
        R.fraction.image_min_aspect_ratio, 1, 1
    )
    val maxAspectRatio = MyApp.instance.resources.getFraction(
        R.fraction.image_max_aspect_ratio, 1, 1
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(dimensionResource(id = R.dimen.card_corner_radius)),
        elevation = CardDefaults.cardElevation(
            defaultElevation = dimensionResource(id = R.dimen.card_elevation)
        )
    ) {
        Box {
            Image(
                painter = rememberAsyncImagePainter(
                    model = image.url,
                    imageLoader = imageLoader
                ),
                contentDescription = stringResource(R.string.gif_content_description),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio.coerceIn(minAspectRatio, maxAspectRatio)),
                contentScale = ContentScale.Crop
            )

            // Номер карточки
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(dimensionResource(id = R.dimen.badge_margin))
                    .size(dimensionResource(id = R.dimen.badge_size))
                    .clip(CircleShape)
                    .background(
                        Color.Black.copy(alpha = 0.2f)
                    ),
                contentAlignment = Alignment.Center
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