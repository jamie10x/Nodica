package com.jamie.nodica.features.groups.group

/**
 * Defines the use cases (business logic operations) related to study groups.
 */
interface GroupUseCase { // Renamed from GroupUseCases for convention

    /**
     * Fetches groups suitable for discovery, potentially applying search and tag filters.
     * Excludes groups the user might already be part of (filtering logic might be here or in VM).
     *
     * @param searchQuery Text to filter group names (case-insensitive).
     * @param tagQuery Text to filter by associated tag names (case-insensitive).
     * @param currentUserId The ID of the user performing the search (used for filtering joined groups).
     * @return A list of [Group] objects matching the criteria. Returns empty list on error.
     */
    suspend fun fetchDiscoverGroups(searchQuery: String, tagQuery: String, currentUserId: String): List<Group>

    /**
     * Attempts to add the specified user to the specified group.
     *
     * @param groupId The ID of the group to join.
     * @param userId The ID of the user joining.
     * @return A [Result] indicating success (Unit) or failure (Exception, potentially [AlreadyJoinedException]).
     */
    suspend fun joinGroup(groupId: String, userId: String): Result<Unit>

    /**
     * Creates a new group with the provided details and links the specified tags.
     * Assumes atomicity is handled by the underlying repository/data source (e.g., via RPC).
     *
     * @param group A [Group] object containing the details for the new group (ID field is ignored). `creatorId` must be set.
     * @param tagIds A list of UUIDs for the tags to be initially associated with the group.
     * @return A [Result] containing the fully created [Group] (including generated ID and relations) on success, or an Exception on failure.
     */
    suspend fun createGroup(group: Group, tagIds: List<String>): Result<Group>
}