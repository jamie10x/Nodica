package com.jamie.nodica.features.groups.group

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns.Companion.raw
import kotlinx.serialization.Serializable

@Serializable
data class UserGroupResponse(
    val groups: Group? = null,
)

class SupabaseGroupRepository(private val client: SupabaseClient) : GroupRepository {

    override suspend fun fetchGroups(): List<Group> {
        return try {
            client.from("groups").select { }  // Selecting all columns
                .decodeList<Group>()
        } catch (e: Exception) {
            throw Exception("Error fetching groups: ${e.message}")
        }
    }

    override suspend fun fetchUserGroups(userId: String): List<Group> {
        return try {
            val response = client.from("group_members").select {
                    raw("*, groups(*)")
                    filter { eq("user_id", userId) }
                }.decodeList<UserGroupResponse>()
            response.mapNotNull { responseItem: UserGroupResponse -> responseItem.groups }
        } catch (e: Exception) {
            throw Exception("Error fetching user groups: ${e.message}")
        }
    }

    override suspend fun joinGroup(groupId: String, userId: String): Result<Unit> {
        return try {
            val existing = client.from("group_members").select {
                    filter {
                        eq("group_id", groupId)
                        eq("user_id", userId)
                    }
                }.decodeList<GroupMembership>()

            if (existing.isEmpty()) {
                client.from("group_members")
                    .insert(mapOf("group_id" to groupId, "user_id" to userId))
                Result.success(Unit)
            } else {
                Result.failure(Exception("Already joined this group"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    override suspend fun createGroup(group: Group): Result<Group> {
        return try {
            val createdGroup = client.from("groups").insert(group).decodeSingle<Group>()
            Result.success(createdGroup)
        } catch (e: Exception) {
            Result.failure(Exception("Error creating group: ${e.message}"))
        }
    }
}

@Serializable
data class GroupMembership(
    val user_id: String,
    val group_id: String,
)
