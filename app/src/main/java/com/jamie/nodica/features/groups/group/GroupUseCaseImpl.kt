package com.jamie.nodica.features.groups.group

class GroupUseCaseImpl(private val repository: GroupRepository) : GroupUseCase {
    override suspend fun getGroups(): Result<List<Group>> = try {
        Result.success(repository.fetchGroups())
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun joinGroup(groupId: String, userId: String): Result<Unit> = try {
        repository.joinGroup(groupId, userId)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun createGroup(group: Group): Result<Group> = try {
        repository.createGroup(group)
    } catch (e: Exception) {
        Result.failure(e)
    }
}