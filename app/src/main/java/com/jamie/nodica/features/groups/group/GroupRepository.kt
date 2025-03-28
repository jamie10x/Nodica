package com.jamie.nodica.features.groups.group

// Interface definition needs to match the implementation
interface GroupRepository {
    // Method for discovering groups (used by HomeScreen/GroupDiscovery)
    suspend fun fetchDiscoverGroups(
        searchQuery: String,
        tagQuery: String,
        currentUserId: String // Needed if excluding joined groups in query (though we filter locally now)
    ): List<Group>

    // Method to fetch groups a specific user has joined (used by GroupsScreen)
    suspend fun fetchUserGroups(userId: String): List<Group>

    // Method to add a user to a group
    suspend fun joinGroup(groupId: String, userId: String): Result<Unit>

    // Method to create a new group, including its tags
    suspend fun createGroup(group: Group, tagIds: List<String>): Result<Group> // Pass tag IDs

    // Method to fetch a single group's details (used after creation)
    suspend fun fetchGroupById(groupId: String): Group?
}