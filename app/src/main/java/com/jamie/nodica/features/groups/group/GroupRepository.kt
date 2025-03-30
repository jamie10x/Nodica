// main/java/com/jamie/nodica/features/groups/group/GroupRepository.kt
package com.jamie.nodica.features.groups.group

// Custom exception for joining when already a member
class AlreadyJoinedException(message: String) : Exception(message)

/**
 * Interface defining data operations for study groups and related entities.
 */
interface GroupRepository {

    /** Fetches groups suitable for discovery, potentially applying filters. */
    suspend fun fetchDiscoverGroups(searchQuery: String, tagQuery: String): List<Group>

    /** Fetches the list of groups the specified user is a member of. */
    suspend fun fetchUserGroups(userId: String): List<Group>

    /** Attempts to add the specified user to the specified group. */
    suspend fun joinGroup(groupId: String, userId: String): Result<Unit>

    /** Creates a new group and associates the initial tags (ideally via RPC). */
    suspend fun createGroup(group: Group, tagIds: List<String>): Result<Group>

    /** Fetches the full details of a single group by its ID. */
    suspend fun fetchGroupById(groupId: String): Group?

    /** Fetches all available tags. */
    suspend fun fetchAllTags(): List<com.jamie.nodica.features.profile.TagItem> // Use full path if needed
}