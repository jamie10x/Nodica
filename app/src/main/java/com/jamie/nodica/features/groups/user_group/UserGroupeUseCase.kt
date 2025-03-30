// main/java/com/jamie/nodica/features/groups/user_group/UserGroupUseCase.kt
package com.jamie.nodica.features.groups.user_group

import com.jamie.nodica.features.groups.group.Group

/** Use case for fetching groups related to a specific user. */
interface UserGroupUseCase {
    /** Fetches the list of groups the user is a member of. */
    suspend fun fetchUserGroups(userId: String): Result<List<Group>>
}