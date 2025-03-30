// main/java/com/jamie/nodica/features/groups/group/GroupItem.kt
package com.jamie.nodica.features.groups.group

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group // Icon for members
import androidx.compose.material.icons.filled.PersonAdd // Icon for "Join Group"
import androidx.compose.material.icons.automirrored.filled.Chat // Icon for "Open Chat"
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
import com.jamie.nodica.ui.theme.NodicaTheme
import kotlinx.datetime.Clock

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class) // For Chips, FlowRow
@Composable
fun GroupItem(
    modifier: Modifier = Modifier, // Allow passing modifiers
    group: Group,
    joined: Boolean, // Is the current user a member?
    onActionClicked: () -> Unit,
    actionTextOverride: String? = null, // Allow custom button text if needed
    isActionInProgress: Boolean = false, // To show loading state on the button
) {
    // Remember derived lists to avoid recalculation on every recomposition unless group.tags changes
    val tagNames = remember(group.tags) { group.tagNames }
    val memberCount = remember(group.membersRelation) { group.memberCount } // Use computed property

    val actionText = actionTextOverride ?: if (joined) "Open Chat" else "Join Group"
    val buttonIcon: ImageVector =
        if (joined) Icons.AutoMirrored.Filled.Chat else Icons.Default.PersonAdd

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) // Use slightly elevated surface
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            // --- Top Row: Name and Member Count ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Group Name (takes up available space, ellipsis if too long)
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleMedium, // Slightly smaller for denser list
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false) // Important for ellipsis
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Member Count Display
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 4.dp) // Align with name baseline
                ) {
                    Icon(
                        Icons.Default.Group,
                        contentDescription = "Members",
                        modifier = Modifier.size(16.dp), // Slightly smaller icon
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "$memberCount", // Use remembered member count
                        style = MaterialTheme.typography.bodySmall, // Match icon size better
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))

            // --- Description (Optional) ---
            if (group.description.isNotBlank()) {
                Text(
                    text = group.description,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2, // Limit lines
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // --- Tags (Optional) using FlowRow ---
            if (tagNames.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    tagNames.take(5).forEach { tagName -> // Limit displayed tags for space
                        // *** FIX: Define border inside composable scope ***
                        val chipBorder = SuggestionChipDefaults.suggestionChipBorder(
                            enabled = true, // Assuming always enabled visually
                            borderWidth = 0.5.dp // Your desired width
                        )
                        SuggestionChip(
                            onClick = { /* Non-interactive */ },
                            label = { Text(tagName, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(24.dp),
                            border = chipBorder // *** Apply the created border ***
                        )
                    }
                    if (tagNames.size > 5) { // Indicator for more tags
                        // *** FIX: Also apply border here if desired ***
                        val indicatorBorder = SuggestionChipDefaults.suggestionChipBorder(
                            enabled = true, borderWidth = 0.5.dp
                        )
                        SuggestionChip(
                            onClick = {},
                            label = { Text("...", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(24.dp),
                            border = indicatorBorder // Apply border
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp)) // Space before button
            }

            // Add space before button if description and tags are BOTH absent
            if (group.description.isBlank() && tagNames.isEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
            }

            // --- Action Button ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End // Align button to the end
            ) {
                // Determine Button Colors based on type
                val buttonColors = if (!joined) { // Primary action: Join
                    ButtonDefaults.buttonColors()
                } else { // Secondary action: Open Chat
                    ButtonDefaults.outlinedButtonColors()
                }
                val contentColor =
                    if (!joined) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary

                Button(
                    // Use filled Button for Join, OutlinedButton for Open Chat later maybe? Or keep consistent? Let's use Button for now.
                    onClick = onActionClicked,
                    enabled = !isActionInProgress,
                    contentPadding = PaddingValues(
                        horizontal = 12.dp, vertical = 8.dp
                    ), // Smaller padding
                    colors = buttonColors // *** FIX: Pass the buttonColors ***
                ) {
                    // Button Content (Loading or Icon+Text)
                    if (isActionInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp), // Smaller indicator
                            strokeWidth = 2.dp,
                            color = contentColor // Use appropriate content color
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (joined) "Opening..." else "Joining...",
                            style = MaterialTheme.typography.labelMedium
                        ) // Smaller text
                    } else {
                        Icon(
                            buttonIcon, contentDescription = null, Modifier.size(18.dp)
                        ) // ButtonDefaults.IconSize is 18.dp
                        Spacer(Modifier.width(6.dp)) // ButtonDefaults.IconSpacing is 8.dp, maybe less needed
                        Text(
                            actionText, style = MaterialTheme.typography.labelMedium
                        ) // Smaller text
                    }
                } // End Button
            } // End Row
        } // End Column
    } // End Card
}

// --- Previews ---

@Preview(name = "Group Item - Discover", showBackground = true, widthDp = 380)
@Composable
private fun GroupItemDiscoverPreview() {
    val previewGroup = Group(
        id = "1",
        name = "Advanced Quantum Physics Study Group for Undergrads",
        description = "Weekly review of Griffith's text, focusing on challenging problems and conceptual understanding.",
        meetingSchedule = "Tue/Thu 8 PM UTC",
        creatorId = "user123",
        createdAt = Clock.System.now(),
        tags = listOf(
            NestedTagDetails(TagNameHolder("Physics")),
            NestedTagDetails(TagNameHolder("Quantum Mechanics")),
            NestedTagDetails(TagNameHolder("University")),
            NestedTagDetails(TagNameHolder("Problem Solving")),
            NestedTagDetails(TagNameHolder("Advanced"))
        ),
        membersRelation = listOf(MemberCountHolder(12))
    )
    NodicaTheme {
        GroupItem(
            group = previewGroup, joined = false, onActionClicked = {}, isActionInProgress = false
        )
    }
}

@Preview(name = "Group Item - Joined", showBackground = true, widthDp = 380)
@Composable
private fun GroupItemJoinedPreview() {
    val previewGroup = Group(
        id = "2",
        name = "Spanish Conversation Practice (B1)",
        description = "Casual conversation practice for intermediate learners.",
        meetingSchedule = "Weekends AM",
        creatorId = "user456",
        createdAt = Clock.System.now(),
        tags = listOf(
            NestedTagDetails(TagNameHolder("Spanish")),
            NestedTagDetails(TagNameHolder("Language Exchange")),
            NestedTagDetails(TagNameHolder("Intermediate"))
        ),
        membersRelation = listOf(MemberCountHolder(8))
    )
    NodicaTheme {
        GroupItem(
            group = previewGroup, joined = true, onActionClicked = {}, isActionInProgress = false
        )
    }
}

@Preview(name = "Group Item - Joining", showBackground = true, widthDp = 380)
@Composable
private fun GroupItemJoiningPreview() {
    val previewGroup = Group(
        id = "3",
        name = "Creative Writing Workshop",
        description = "Share your work and get feedback.",
        creatorId = "creator",
        createdAt = Clock.System.now(),
        tags = listOf(
            NestedTagDetails(TagNameHolder("Writing")),
            NestedTagDetails(TagNameHolder("Fiction")),
            NestedTagDetails(TagNameHolder("Poetry"))
        ),
        membersRelation = listOf(MemberCountHolder(5))
    )
    NodicaTheme {
        GroupItem(
            group = previewGroup, joined = false, onActionClicked = {}, isActionInProgress = true
        )
    }
}

@Preview(name = "Group Item - No Desc/Tags", showBackground = true, widthDp = 380)
@Composable
private fun GroupItemNoDescTagsPreview() {
    val previewGroup = Group(
        id = "4", name = "Quick Math Problems", description = "", // No description
        creatorId = "creator", createdAt = Clock.System.now(), tags = emptyList(), // No tags
        membersRelation = listOf(MemberCountHolder(3))
    )
    NodicaTheme {
        GroupItem(
            group = previewGroup, joined = false, onActionClicked = {}, isActionInProgress = false
        )
    }
}