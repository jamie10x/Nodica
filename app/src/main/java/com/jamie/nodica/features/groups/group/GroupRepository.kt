package com.jamie.nodica.features.groups.group

interface GroupRepository {
    suspend fun fetchGroups(): List<Group>
    suspend fun fetchUserGroups(userId: String): List<Group>
    suspend fun joinGroup(groupId: String, userId: String): Result<Unit>
    suspend fun createGroup(group: Group): Result<Group>
}
