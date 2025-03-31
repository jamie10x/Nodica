// main/java/com/jamie/nodica/features/groups/group/GroupUseCaseImpl.kt
package com.jamie.nodica.features.groups.group

import timber.log.Timber

class GroupUseCaseImpl(private val repository: GroupRepository) : GroupUseCase {

    override suspend fun fetchDiscoverGroups(searchQuery: String, tagQuery: String): Result<List<Group>> {
        return try {
            val groups = repository.fetchDiscoverGroups(searchQuery.trim(), tagQuery.trim())
            Result.success(groups)
        } catch (e: Exception) {
            Timber.e(e, "UseCase: Error fetching discover groups.")
            Result.failure(Exception("Failed to find groups. ${e.message}", e))
        }
    }

    override suspend fun joinGroup(groupId: String, userId: String): Result<Unit> {
        if (groupId.isBlank() || userId.isBlank()) {
            return Result.failure(IllegalArgumentException("Group and User IDs cannot be blank."))
        }
        // Repository handles AlreadyJoinedException and other errors
        return repository.joinGroup(groupId, userId)
    }

    override suspend fun createGroup(group: Group, tagIds: List<String>): Result<Group> {
        val trimmedName = group.name.trim()
        if (trimmedName.isBlank()) {
            Timber.e("UseCase: createGroup validation failed - Group name is blank.")
            return Result.failure(IllegalArgumentException("Group name cannot be blank."))
        }
        // Ensure creatorId is present before passing to repository
        if (group.creatorId.isBlank()) {
            Timber.e("UseCase: createGroup validation failed - Creator ID is blank.")
            return Result.failure(IllegalStateException("Group creator ID is missing."))
        }

        Timber.d("UseCase: Calling repository.createGroup for name='${group.name}', creator='${group.creatorId}'")
        val groupToCreate = group.copy(
            name = trimmedName,
            description = group.description.trim(),
            meetingSchedule = group.meetingSchedule?.trim()?.ifBlank { null }
        )
        // Delegate to repository, passing the validated Group object and distinct tag IDs
        return repository.createGroup(groupToCreate, tagIds.distinct())
    }
}