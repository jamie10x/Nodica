package com.jamie.nodica.features.groups.user_group

import com.jamie.nodica.features.groups.group.Group
import com.jamie.nodica.features.groups.group.GroupRepository
import timber.log.Timber

class UserGroupUseCaseImpl(private val repository: GroupRepository) : UserGroupUseCase {

    /**
     * Fetches the groups associated with the given user ID.
     */
    override suspend fun fetchUserGroups(userId: String): Result<List<Group>> {
        if (userId.isBlank()) {
            return Result.failure(IllegalArgumentException("User ID cannot be blank."))
        }
        return try {
            // Delegate fetching to the repository
            val groups = repository.fetchUserGroups(userId)
            Result.success(groups) // Repository handles sorting or ViewModel can sort
        } catch (e: Exception) {
            Timber.e(e, "UseCase: Error fetching user groups for user $userId")
            // Return failure with the original exception or a user-friendly message
            Result.failure(Exception("Failed to load your groups.", e))
        }
    }
}