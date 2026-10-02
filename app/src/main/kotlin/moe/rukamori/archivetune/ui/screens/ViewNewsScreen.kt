/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * ArchiveTune (2026) | Original work by © Rukamori
 * GPL-3.0 License | Contributors: see git history
 */

@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)

package moe.rukamori.archivetune.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.models.NewsItem
import moe.rukamori.archivetune.ui.component.MarkdownText
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.viewmodels.ViewNewsUiState
import moe.rukamori.archivetune.viewmodels.ViewNewsViewModel
import moe.rukamori.archivetune.ui.component.IconButton as AppIconButton

@Composable
fun ViewNewsScreen(
    navController: NavController,
    viewModel: ViewNewsViewModel = hiltViewModel(),
) {
    val contentState by viewModel.contentState.collectAsStateWithLifecycle()
    val newsItem = viewModel.newsItem
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier =
            Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = newsItem?.title ?: "",
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    AppIconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = stringResource(R.string.back_button_desc),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        AnimatedContent(
            targetState = contentState,
            transitionSpec = {
                fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) togetherWith
                    fadeOut(spring(stiffness = Spring.StiffnessMediumLow))
            },
            modifier = Modifier.fillMaxSize(),
            label = "viewNewsContent",
        ) { state ->
            when (state) {
                is ViewNewsUiState.Loading -> {
                    ViewNewsLoadingState(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                    )
                }

                is ViewNewsUiState.Error -> {
                    ViewNewsErrorState(
                        message = state.message,
                        onRetry = viewModel::loadContent,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                    )
                }

                is ViewNewsUiState.Success -> {
                    ViewNewsArticleContent(
                        newsItem = newsItem,
                        content = state.content,
                        innerPadding = innerPadding,
                    )
                }
            }
        }
    }
}
@Composable
private fun ViewNewsArticleContent(
    newsItem: NewsItem?,
    content: String,
    innerPadding: PaddingValues,
) {
    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                ),
    ) {
        val horizontalPadding = if (maxWidth > 840.dp) (maxWidth - 760.dp) / 2 else 24.dp
        val imageUrls = newsItem?.imageUrls.orEmpty()
        var fullImageUrl by remember { mutableStateOf<String?>(null) }

        LazyColumn(
            contentPadding =
                PaddingValues(
                    top = innerPadding.calculateTopPadding() + 16.dp,
                    bottom = innerPadding.calculateBottomPadding() + 48.dp,
                ),
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (newsItem != null) {
                item(key = "article_meta", contentType = "meta") {
                    ViewNewsMetaRow(
                        item = newsItem,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = horizontalPadding),
                    )
                }
            }

            if (imageUrls.isNotEmpty()) {
                item(key = "article_carousel", contentType = "carousel") {
                    ViewNewsCarousel(
                        imageUrls = imageUrls,
                        onImageClick = { url -> fullImageUrl = url },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(320.dp),
                    )
                }
            }

            item(key = "article_content", contentType = "markdown") {
                MarkdownText(
                    markdown = content,
                    style =
                        MaterialTheme.typography.bodyLarge.copy(
                            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.2f,
                        ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = horizontalPadding),
                )
            }
        }

        if (fullImageUrl != null) {
            ViewNewsFullImageDialog(
                imageUrl = fullImageUrl!!,
                onDismiss = { fullImageUrl = null },
            )
        }
    }
}
