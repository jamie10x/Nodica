// main/java/com/jamie/nodica/features/groups/user_group/UserGroupUseCaseImpl.kt
package com.jamie.nodica.features.groups.user_group

import com.jamie.nodica.features.groups.group.Group
import com.jamie.nodica.features.groups.group.GroupRepository
import timber.log.Timber

class UserGroupUseCaseImpl(private val repository: GroupRepository) : UserGroupUseCase {

    override suspend fun fetchUserGroups(userId: String): Result<List<Group>> {
        if (userId.isBlank()) {
            Timber.w("UseCase: fetchUserGroups called with blank userId.")
            return Result.failure(IllegalArgumentException("User ID cannot be blank."))
        }
        return try {
            val groups = repository.fetchUserGroups(userId)
            Result.success(groups)
        } catch (e: Exception) {
            Timber.e(e, "UseCase: Error fetching user groups for user $userId: ${e.message}")
            Result.failure(Exception("Failed to load your groups. ${e.message}", e))
        }
    }
}