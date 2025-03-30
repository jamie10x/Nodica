package com.jamie.nodica.features.groups.group

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat // Icon for "Open Chat"
import androidx.compose.material.icons.filled.Group // Icon for members
import androidx.compose.material.icons.filled.PersonAdd // Icon for "Join Group"
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.FlowRow // Use specific import
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.automirrored.filled.Chat
import com.jamie.nodica.ui.theme.NodicaTheme // For preview
import kotlinx.datetime.Clock // For preview

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class) // For Chips, FlowRow
@Composable
fun GroupItem(
    group: Group,
    joined: Boolean,
    onActionClicked: () -> Unit,
    actionText: String = if (joined) "Open Chat" else "Join Group",
    isActionInProgress: Boolean = false // To show loading state on the button
) {
    // Remember derived list to avoid recalculation on every recomposition unless group.tags changes
    val tagNames = remember(group.tags) { group.tagNames }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp), // Subtle elevation
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer) // Slightly different bg
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Group Name and Member Count
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1, // Prevent name wrapping awkwardly
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false) // Allow shrinking but don't force expansion
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Member count chip/indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 4.dp) // Add padding if needed
                ) {
                    Icon(
                        Icons.Default.Group,
                        contentDescription = "Members",
                        modifier = Modifier.size(18.dp), // Consistent icon size
                        tint = MaterialTheme.colorScheme.onSurfaceVariant // Muted color
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    // Use memberCount helper property
                    Text(
                        "${group.memberCount}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Description (Show only if present)
            if (group.description.isNotBlank()) {
                Text(
                    text = group.description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3, // Limit lines to prevent excessive height
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant // Muted color
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Tags (Show only if present)
            if (tagNames.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    tagNames.take(6).forEach { tagName -> // Limit displayed tags if needed
                        SuggestionChip( // Use SuggestionChip for non-interactive display
                            onClick = { /* Non-interactive */ },
                            label = { Text(tagName, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                    if (tagNames.size > 6) { // Indicate if more tags exist
                        SuggestionChip(onClick = {}, label = { Text("...", style = MaterialTheme.typography.labelSmall) })
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                // Add space even if no description or tags, before the button
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Action Button (Join / Open Chat) - Aligned to the end
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End // Align button to the end
            ) {
                val buttonIcon: ImageVector = if (joined) Icons.AutoMirrored.Filled.Chat else Icons.Default.PersonAdd
                // Choose Button type based on 'joined' status
                val buttonContent: @Composable RowScope.() -> Unit = {
                    if (isActionInProgress) {
                        // Show loading indicator
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = if (joined) LocalContentColor.current else MaterialTheme.colorScheme.onPrimary // Adjust color
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if(joined) "Opening..." else "Joining...") // Reflect action
                    } else {
                        // Show icon and text
                        Icon(buttonIcon, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(actionText)
                    }
                }

                if (!joined) {
                    Button(onClick = onActionClicked, enabled = !isActionInProgress, content = buttonContent)
                } else {
                    OutlinedButton(onClick = onActionClicked, enabled = !isActionInProgress, content = buttonContent)
                }
            }
        }
    }
}


// Previews
@Preview(name = "Group Item - Discover", showBackground = true)
@Composable
fun GroupItemDiscoverPreview() {
    val previewGroup = Group(
        id = "1", name = "Advanced Calculus Study Group", description = "Preparing for final exams, focus on integration techniques and series.",
        meetingSchedule = "Mon/Wed 7 PM EST", creatorId = "user123", createdAt = Clock.System.now(),
        tags = listOf(NestedTagDetails(TagNameHolder("Calculus")), NestedTagDetails(TagNameHolder("Mathematics")), NestedTagDetails(TagNameHolder("Exams"))),
        membersRelation = listOf(MemberCountHolder(15))
    )
    NodicaTheme {
        GroupItem(group = previewGroup, joined = false, onActionClicked = {}, isActionInProgress = false)
    }
}

@Preview(name = "Group Item - Joined", showBackground = true)
@Composable
fun GroupItemJoinedPreview() {
    val previewGroup = Group(
        id = "2", name = "Kotlin Beginners", description = "Let's learn Kotlin together!",
        meetingSchedule = "Weekends", creatorId = "user456", createdAt = Clock.System.now(),
        tags = listOf(NestedTagDetails(TagNameHolder("Kotlin")), NestedTagDetails(TagNameHolder("Programming"))),
        membersRelation = listOf(MemberCountHolder(8))
    )
    NodicaTheme {
        GroupItem(group = previewGroup, joined = true, onActionClicked = {}, isActionInProgress = false)
    }
}

@Preview(name = "Group Item - Joining", showBackground = true)
@Composable
fun GroupItemJoiningPreview() {
    val previewGroup = Group(
        id = "3", name = "IELTS Speaking Practice", description = "Daily practice sessions for IELTS speaking module.",
        tags = listOf(NestedTagDetails(TagNameHolder("IELTS")), NestedTagDetails(TagNameHolder("English"))),
        membersRelation = listOf(MemberCountHolder(25))
    )
    NodicaTheme {
        GroupItem(group = previewGroup, joined = false, onActionClicked = {}, isActionInProgress = true)
    }
}