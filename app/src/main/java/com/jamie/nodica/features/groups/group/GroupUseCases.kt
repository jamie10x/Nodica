// main/java/com/jamie/nodica/features/groups/group/GroupUseCase.kt
package com.jamie.nodica.features.groups.group

/** Use cases for general group operations (discovery, creation, joining). */
interface GroupUseCase {
    /** Fetches groups for discovery. */
    suspend fun fetchDiscoverGroups(searchQuery: String, tagQuery: String): Result<List<Group>>
    /** Attempts to join a group. */
    suspend fun joinGroup(groupId: String, userId: String): Result<Unit>
    /** Creates a new group. */
    suspend fun createGroup(group: Group, tagIds: List<String>): Result<Group>
}