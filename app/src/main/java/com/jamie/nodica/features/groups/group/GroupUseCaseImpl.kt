// Corrected: main/java/com/jamie/nodica/features/groups/group/GroupUseCaseImpl.kt
package com.jamie.nodica.features.groups.group

import timber.log.Timber

class GroupUseCaseImpl(private val repository: GroupRepository) : GroupUseCase {

    // IMPLEMENTATION for the new interface method
    override suspend fun fetchDiscoverGroups(searchQuery: String, tagQuery: String, currentUserId: String): List<Group> {
        return try {
            repository.fetchDiscoverGroups(searchQuery, tagQuery, currentUserId)
        } catch (e: Exception) {
            Timber.e(e, "UseCase error fetching discover groups")
            // Depending on how you want to handle errors, you might:
            // 1. Return emptyList(): emptyList()
            // 2. Rethrow: throw e
            // 3. Wrap and throw: throw RuntimeException("Failed to discover groups", e)
            // Returning empty list might be safer for the UI flow.
            emptyList()
        }
    }

    // joinGroup implementation remains the same
    override suspend fun joinGroup(groupId: String, userId: String): Result<Unit> = try {
        repository.joinGroup(groupId, userId)
    } catch (e: Exception) {
        Timber.e(e, "UseCase error joining group")
        Result.failure(e)
    }

    // createGroup implementation remains the same
    override suspend fun createGroup(group: Group, tagIds: List<String>): Result<Group> = try {
        repository.createGroup(group, tagIds)
    } catch (e: Exception) {
        Timber.e(e, "UseCase error creating group")
        Result.failure(e)
    }
}