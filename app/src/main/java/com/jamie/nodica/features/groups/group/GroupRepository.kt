package com.jamie.nodica.features.groups.group

// Interface definition - no changes needed here if signatures are correct.
interface GroupRepository {
    // Method for discovering groups
    suspend fun fetchDiscoverGroups(
        searchQuery: String,
        tagQuery: String,
        // currentUserId: String // No longer strictly needed here if filtering happens in ViewModel
    ): List<Group>

    // Method to fetch groups a specific user has joined
    suspend fun fetchUserGroups(userId: String): List<Group>

    // Method to add a user to a group
    suspend fun joinGroup(groupId: String, userId: String): Result<Unit>

    // Method to create a new group, linking tags via RPC is preferred now
    // This signature might change if RPC handles everything
    suspend fun createGroup(group: Group, tagIds: List<String>): Result<Group> // Keep for now, maybe adapt later if RPC takes over fully

    // Method to fetch a single group's details (used after creation or for detail view)
    suspend fun fetchGroupById(groupId: String): Group?
}