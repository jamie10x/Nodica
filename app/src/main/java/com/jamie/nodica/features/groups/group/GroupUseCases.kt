package com.jamie.nodica.features.groups.group

interface GroupUseCase {
    suspend fun getGroups(): Result<List<Group>>
    suspend fun joinGroup(groupId: String, userId: String): Result<Unit>
    suspend fun createGroup(group: Group): Result<Group>
}