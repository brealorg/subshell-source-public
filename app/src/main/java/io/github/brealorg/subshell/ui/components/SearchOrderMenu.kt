package io.github.brealorg.subshell.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.brealorg.subshell.data.RedditListingTimePeriod
import io.github.brealorg.subshell.data.RedditProfilePostSort
import io.github.brealorg.subshell.data.RedditProfilePostsOrder
import io.github.brealorg.subshell.data.RedditSearchOrder
import io.github.brealorg.subshell.data.RedditSearchSort
import io.github.brealorg.subshell.ui.symbols.Symbols

/**
 * ISSUE289_SEARCH_RESULTS_CAN_BE_SORTED_V1 / ISSUE289_PROFILE_POSTS_CAN_BE_SORTED_V1 (#289): the
 * order of a list of posts, beside the layout choice. A sort that takes a time range also shows the
 * range rows; the menu closes when the choice is complete.
 */
@Composable
internal fun SearchOrderMenuButton(
    order: RedditSearchOrder,
    onOrderChange: (RedditSearchOrder) -> Unit,
    modifier: Modifier = Modifier,
) = PostOrderMenuButton(
    sorts = RedditSearchSort.entries,
    sort = order.sort,
    sortLabel = RedditSearchSort::label,
    sortTakesRange = RedditSearchSort::supportsTimePeriod,
    timePeriod = order.timePeriod,
    onSortChange = { sort -> onOrderChange(order.copy(sort = sort)) },
    onTimePeriodChange = { period -> onOrderChange(order.copy(timePeriod = period)) },
    modifier = modifier,
)

@Composable
internal fun ProfilePostsOrderMenuButton(
    order: RedditProfilePostsOrder,
    onOrderChange: (RedditProfilePostsOrder) -> Unit,
    modifier: Modifier = Modifier,
) = PostOrderMenuButton(
    sorts = RedditProfilePostSort.entries,
    sort = order.sort,
    sortLabel = RedditProfilePostSort::label,
    sortTakesRange = RedditProfilePostSort::supportsTimePeriod,
    timePeriod = order.timePeriod,
    onSortChange = { sort -> onOrderChange(order.copy(sort = sort)) },
    onTimePeriodChange = { period -> onOrderChange(order.copy(timePeriod = period)) },
    modifier = modifier,
)

@Composable
private fun <S> PostOrderMenuButton(
    sorts: List<S>,
    sort: S,
    sortLabel: (S) -> String,
    sortTakesRange: (S) -> Boolean,
    timePeriod: RedditListingTimePeriod,
    onSortChange: (S) -> Unit,
    onTimePeriodChange: (RedditListingTimePeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(Symbols.Sort, contentDescription = "Sort posts: ${sortLabel(sort)}")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(POST_ORDER_MENU_WIDTH),
        ) {
            sorts.forEach { candidate ->
                DropdownMenuItem(
                    text = { Text(sortLabel(candidate)) },
                    modifier = Modifier.semantics { selected = candidate == sort },
                    trailingIcon = if (candidate == sort) {
                        { Icon(Symbols.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    onClick = {
                        onSortChange(candidate)
                        if (!sortTakesRange(candidate)) expanded = false
                    },
                )
            }
            if (sortTakesRange(sort)) {
                HorizontalDivider()
                RedditListingTimePeriod.entries.forEach { period ->
                    DropdownMenuItem(
                        text = { Text(period.label) },
                        modifier = Modifier.semantics { selected = period == timePeriod },
                        trailingIcon = if (period == timePeriod) {
                            { Icon(Symbols.Check, contentDescription = null) }
                        } else {
                            null
                        },
                        onClick = {
                            onTimePeriodChange(period)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

private val POST_ORDER_MENU_WIDTH = 200.dp
