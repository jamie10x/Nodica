// main/java/com/jamie/nodica/features/groups/group/GroupUseCaseImpl.kt
package com.jamie.nodica.features.groups.group

import timber.log.Timber

class GroupUseCaseImpl(private val repository: GroupRepository) : GroupUseCase {

    /** Fetches discoverable groups, applying trims to search/tag queries. */
    override suspend fun fetchDiscoverGroups(searchQuery: String, tagQuery: String, currentUserId: String): List<Group> {
        // currentUserId validation is now primarily handled in ViewModel/Repo/RLS. Pass it along if needed by repo logic beyond basic RLS.
        if (currentUserId.isBlank()) {
            Timber.w("UseCase: fetchDiscoverGroups called with blank currentUserId. RLS should prevent data leakage, but discovery might be impaired.")
            // Decide if fetching without userId is acceptable for discovery or return empty
            // return emptyList()
        }
        return try {
            // Trim search/tag queries before passing to repository
            repository.fetchDiscoverGroups(
                searchQuery = searchQuery.trim(),
                tagQuery = tagQuery.trim()
                // Pass currentUserId ONLY if repository logic needs it beyond simple RLS checks
            )
        } catch (e: Exception) {
            Timber.e(e, "UseCase: Error fetching discover groups. Search='$searchQuery', Tag='$tagQuery'")
            emptyList() // Return empty list on failure to prevent UI crash
        }
    }

    /** Joins a group after validating IDs. */
    override suspend fun joinGroup(groupId: String, userId: String): Result<Unit> {
        // Validate input IDs
        if (groupId.isBlank() || userId.isBlank()) {
            val errorMsg = "UseCase: Invalid input - Group ID and User ID cannot be blank."
            Timber.w(errorMsg)
            return Result.failure(IllegalArgumentException(errorMsg))
        }
        // Delegate directly to repository, which handles logic and error wrapping
        return repository.joinGroup(groupId, userId)
    }

    /** Creates a group after validating essential fields. */
    override suspend fun createGroup(group: Group, tagIds: List<String>): Result<Group> {
        // Validate required fields for group creation
        val trimmedName = group.name.trim()
        if (trimmedName.isBlank()) {
            Timber.w("UseCase: Group name cannot be blank.")
            return Result.failure(IllegalArgumentException("Group name cannot be blank."))
        }
        if (group.creatorId.isNullOrBlank()) {
            Timber.w("UseCase: Group creator ID is missing.")
            return Result.failure(IllegalStateException("Group creator ID is missing."))
        }
        // Optional: Validate tagIds are UUIDs format if desired, though DB handles actual FK check.
        // if (tagIds.any { !isValidUUID(it) }) { // Assuming isValidUUID function exists
        //     return Result.failure(IllegalArgumentException("Invalid tag ID format provided."))
        // }

        // Prepare the group object with potentially trimmed description/schedule
        val groupToCreate = group.copy(
            name = trimmedName,
            description = group.description.trim(), // Trim description
            meetingSchedule = group.meetingSchedule?.trim()?.ifBlank { null } // Trim optional schedule
            // ID is ignored by repository's create logic, creatorId must be present
        )

        // Delegate creation to the repository, pass distinct tag IDs
        return repository.createGroup(groupToCreate, tagIds.distinct())
    }
}