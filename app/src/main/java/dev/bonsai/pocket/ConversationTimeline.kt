package dev.bonsai.pocket

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun ConversationTimeline(
    conversationId: String,
    messageCount: Int,
    revision: Int,
    followRequest: Int,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit
) {
    val scope = rememberCoroutineScope()
    var following by rememberSaveable(conversationId) { mutableStateOf(true) }
    var dragging by remember(conversationId) { mutableStateOf(false) }
    LaunchedEffect(state, conversationId) {
        state.interactionSource.interactions.collect { event ->
            when (event) {
                is DragInteraction.Start -> { dragging = true; following = false }
                is DragInteraction.Stop, is DragInteraction.Cancel -> dragging = false
            }
        }
    }
    LaunchedEffect(state, conversationId) {
        snapshotFlow { !state.isScrollInProgress && !state.canScrollForward }
            .distinctUntilChanged().collect { atEnd -> if (atEnd && !dragging) following = true }
    }
    LaunchedEffect(conversationId, followRequest) {
        following = true
        withFrameNanos { }
        if (!dragging) state.scrollToItem(messageCount)
    }
    LaunchedEffect(conversationId, messageCount, revision, following) {
        if (following && !dragging) {
            withFrameNanos { }
            if (following && !dragging) state.scrollToItem(messageCount)
        }
    }
    Box(modifier) {
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize().testTag("conversation"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            content()
            // A growing reply can be taller than the viewport. Target its end, not its start.
            item(key = "$conversationId:tail") { Spacer(Modifier.fillMaxWidth().height(1.dp).testTag("conversationEnd")) }
        }
        if (state.canScrollForward) SmallFloatingActionButton(
            onClick = {
                following = true
                scope.launch { state.scrollToItem(messageCount) }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp).testTag("jumpToLatest")
        ) { Icon(Icons.Outlined.KeyboardArrowDown, "Latest message") }
    }
}
