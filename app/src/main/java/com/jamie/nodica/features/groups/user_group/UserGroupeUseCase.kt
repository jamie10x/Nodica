package com.jamie.nodica.features.groups.user_group

import com.jamie.nodica.features.groups.group.Group

interface UserGroupUseCase {
    suspend fun fetchUserGroups(userId: String): Result<List<Group>> // ✅ Make sure this exists
}
