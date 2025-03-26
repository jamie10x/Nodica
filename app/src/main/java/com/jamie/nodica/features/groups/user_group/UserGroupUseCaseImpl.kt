package com.jamie.nodica.features.groups.user_group

import com.jamie.nodica.features.groups.group.Group
import com.jamie.nodica.features.groups.group.GroupRepository

class UserGroupUseCaseImpl(private val repository: GroupRepository) : UserGroupUseCase {

    override suspend fun fetchUserGroups(userId: String): Result<List<Group>> {
        return try {
            Result.success(repository.fetchUserGroups(userId))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}