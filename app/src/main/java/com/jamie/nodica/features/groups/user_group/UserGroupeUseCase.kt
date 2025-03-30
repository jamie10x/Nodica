package com.jamie.nodica.features.groups.user_group // Correct package

import com.jamie.nodica.features.groups.group.Group

/**
 * Use case for fetching groups related to a specific user (e.g., groups they have joined).
 */
interface UserGroupUseCase { // Renamed interface
    /**
     * Fetches the list of groups the specified user is a member of.
     * @param userId The UUID of the user.
     * @return A Result containing the list of Groups on success, or an Exception on failure.
     */
    suspend fun fetchUserGroups(userId: String): Result<List<Group>>
}