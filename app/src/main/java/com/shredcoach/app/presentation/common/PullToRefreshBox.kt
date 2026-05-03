package com.shredcoach.app.presentation.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.shredcoach.app.presentation.theme.OrangeVibrant
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Wrapper qui ajoute le pull-to-refresh a n'importe quel contenu scrollable.
 * Garantit une duree minimum de 1 seconde pour le feedback visuel.
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun PullToRefreshBox(
    onRefresh: suspend () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }

    val pullState = rememberPullRefreshState(
        refreshing = refreshing,
        onRefresh = {
            if (!refreshing) {
                refreshing = true
                scope.launch {
                    val start = System.currentTimeMillis()
                    onRefresh()
                    // Duree minimum 1 seconde pour feedback visuel
                    val elapsed = System.currentTimeMillis() - start
                    if (elapsed < 1000) delay(1000 - elapsed)
                    refreshing = false
                }
            }
        }
    )

    Box(
        modifier = modifier.fillMaxSize().pullRefresh(pullState),
        contentAlignment = Alignment.TopCenter
    ) {
        content()
        PullRefreshIndicator(
            refreshing = refreshing,
            state = pullState,
            modifier = Modifier.align(Alignment.TopCenter),
            backgroundColor = MaterialTheme.colorScheme.surface,
            contentColor = OrangeVibrant
        )
    }
}
