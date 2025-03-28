// Corrected: main/java/com/jamie/nodica/features/groups/group/GroupItem.kt

package com.jamie.nodica.features.groups.group

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group // Icon for members
// import androidx.compose.material.icons.filled.Info // Not used if showing button instead
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.FlowRow // Use specific import
import androidx.compose.foundation.layout.ExperimentalLayoutApi

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class) // For FilterChip, FlowRow
@Composable
fun GroupItem(
    group: Group,
    joined: Boolean,
    onActionClicked: () -> Unit, // Parameter IS used in Button/OutlinedButton onClick
    actionText: String = if (joined) "Open Chat" else "Join Group" // Parameter IS used in Text
) {
    val tagNames = remember(group.tags) { group.tagNames }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Group Name and Member Count
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = group.name, style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Group, "Members", Modifier.size(18.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("${group.members}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Description
            if (group.description.isNotBlank()) {
                Text(group.description, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))
            } // FIX: Removed empty 'else {}' block if it existed implicitly or explicitly

            // Tags
            if (tagNames.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    tagNames.forEach { tagName ->
                        SuggestionChip(
                            onClick = { }, // onClick is required for SuggestionChip
                            label = { Text(tagName, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Action Button or Status Text
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                // FIX: Use standard function call parentheses if needed, check alignment logic
                // horizontalArrangement = if (!joined) Arrangement.End else Arrangement.Start // This logic seems fine
                horizontalArrangement = Arrangement.End // Always align button/text to end for consistency? Or keep conditional.
            ) {
                if (!joined) {
                    Button(onClick = onActionClicked) { // Usage of onActionClicked
                        Text(actionText) // Usage of actionText
                    }
                } else {
                    OutlinedButton(onClick = onActionClicked) { // Usage of onActionClicked
                        Text(actionText) // Usage of actionText
                    }
                }
            }
        }
    }
}