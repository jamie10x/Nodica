// Corrected: main/java/com/jamie/nodica/features/groups/group/GroupUseCases.kt
package com.jamie.nodica.features.groups.group

// GroupUseCase interface now includes discovery method
interface GroupUseCase {
    // Fetches groups for discovery (with potential filters)
    suspend fun fetchDiscoverGroups(searchQuery: String, tagQuery: String, currentUserId: String): List<Group> // ADD THIS LINE

    // Joins a user to a group
    suspend fun joinGroup(groupId: String, userId: String): Result<Unit>

    // Creates a new group
    suspend fun createGroup(group: Group, tagIds: List<String>): Result<Group>
}