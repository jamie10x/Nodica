package com.jamie.nodica.features.groups.group

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.postgrest // Import postgrest explicitly
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Count
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.supabaseJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import timber.log.Timber

// Represents the response when fetching group memberships with nested group data
@Serializable
private data class UserGroupMembershipResponse(
    val groups: Group? = null // Groups is nullable in case the join fails or group deleted
)

// Represents the structure of the group_members table for insertion
@Serializable
private data class GroupMembershipInsert(
    // Use snake_case as expected by DB insert, Kotlin convention warnings can be suppressed if desired
    val user_id: String,
    val group_id: String
)

// Custom exception for already joined scenario
class AlreadyJoinedException(message: String) : Exception(message)

class SupabaseGroupRepository(supabaseClient: SupabaseClient) : GroupRepository {

    private val db = supabaseClient.postgrest // Convenience accessor for postgrest

    override suspend fun fetchDiscoverGroups(
        searchQuery: String,
        tagQuery: String,
    ): List<Group> {
        val cleanedSearch = searchQuery.trim()
        val cleanedTag = tagQuery.trim()
        Timber.d("Repo: Fetching discover groups. Search: '$cleanedSearch', Tag: '$cleanedTag'")
        return try {
            val selectQuery = "*, member_count:group_members(count), tags:group_tags!inner(tags(name))"

            db.from("groups").select(columns = Columns.raw(selectQuery)) {
                // Apply filters using the filter DSL block
                filter {
                    if (cleanedSearch.isNotBlank()) {
                        // Filter on the 'name' column of the 'groups' table
                        ilike("name", "%${cleanedSearch}%")
                    }
                    if (cleanedTag.isNotBlank()) {
                        // **CORRECTED SYNTAX**: Filter on the 'name' column of the related 'tags' table
                        // The path "tags.name" refers to the joined relation path.
                        ilike("tags.name", "%${cleanedTag}%")
                    }
                    // You can chain multiple conditions like:
                    // eq("some_other_column", someValue)
                }
                order("created_at", Order.DESCENDING)
                limit(50) // Limit results for discovery performance
            }.decodeList<Group>()

        } catch (e: Exception) {
            Timber.e(e, "Repo: Error fetching discover groups")
            throw Exception("Could not fetch groups: ${e.message}")
        }
    }

    override suspend fun fetchUserGroups(userId: String): List<Group> {
        Timber.d("Repo: Fetching groups for user: $userId")
        return try {
            val response = db.from("group_members").select(
                columns = Columns.raw("groups!inner(*, member_count:group_members(count), tags:group_tags(tags(name)))")
            ) {
                filter { eq("user_id", userId) }
                order("groups.name", Order.ASCENDING) // Order user's groups by name
            }.decodeList<UserGroupMembershipResponse>()

            response.mapNotNull { it.groups }

        } catch (e: Exception) {
            Timber.e(e, "Repo: Error fetching user groups for user $userId")
            throw Exception("Could not load your groups: ${e.message}")
        }
    }

    override suspend fun joinGroup(groupId: String, userId: String): Result<Unit> {
        Timber.d("Repo: Attempting to join group $groupId for user $userId")
        return try {
            val existingCount = db.from("group_members")
                .select {
                    filter {
                        eq("group_id", groupId)
                        eq("user_id", userId)
                    }
                    count(Count.EXACT)
                }.countOrNull()

            if (existingCount != null && existingCount > 0) {
                Timber.w("User $userId already a member of group $groupId")
                return Result.failure(AlreadyJoinedException("You are already a member of this group."))
            }

            // Use the private data class with snake_case if needed by insert
            db.from("group_members").insert(GroupMembershipInsert(user_id = userId, group_id = groupId))
            Timber.i("User $userId successfully joined group $groupId")
            Result.success(Unit)

        } catch (e: RestException) {
            Timber.e(e, "Repo: DB error joining group $groupId for user $userId")
            Result.failure(Exception("Database error joining group: ${e.description ?: e.message}"))
        } catch (e: Exception) {
            Timber.e(e, "Repo: Generic error joining group $groupId for user $userId")
            Result.failure(Exception("Could not join group: ${e.message}"))
        }
    }

    @OptIn(SupabaseInternal::class)
    override suspend fun createGroup(group: Group, tagIds: List<String>): Result<Group> {
        val creatorId = group.creatorId ?: return Result.failure(IllegalArgumentException("Creator ID cannot be null"))
        Timber.d("Repo: Calling create_group_and_add_tags RPC. Name='${group.name}', Creator='$creatorId', Tags='${tagIds}'")

        return try {
            val rpcParams = buildJsonObject {
                put("p_name", group.name)
                put("p_description", group.description)
                put("p_meeting_schedule", group.meetingSchedule)
                put("p_creator_id", creatorId)
                put("p_tag_ids", Json.encodeToString(tagIds.distinct()))
            }
            Timber.v("RPC Params: $rpcParams")

            val result = db.rpc("create_group_and_add_tags", rpcParams)
            Timber.v("RPC Result Data: ${result.data}")

            val resultBody = supabaseJson.parseToJsonElement(result.data)
            val newGroupId = resultBody.jsonObject["new_group_id"]?.jsonPrimitive?.contentOrNull
                ?: throw Exception("RPC 'create_group_and_add_tags' did not return the 'new_group_id'. Response: ${result.data}")

            Timber.i("Group created via RPC with ID: $newGroupId")

            val finalGroup = fetchGroupById(newGroupId)
                ?: throw Exception("Failed to fetch group details after RPC creation (ID: $newGroupId).")

            Result.success(finalGroup)

        } catch (e: RestException) {
            Timber.e(e, "Repo: DB error calling create_group_and_add_tags RPC")
            Result.failure(Exception("Database error creating group: ${e.description ?: e.message}"))
        } catch (e: Exception) {
            Timber.e(e, "Repo: Generic error creating group via RPC '${group.name}'")
            Result.failure(Exception("Error creating group: ${e.message}"))
        }
    }

    override suspend fun fetchGroupById(groupId: String): Group? {
        Timber.d("Repo: Fetching group by ID: $groupId")
        return try {
            db.from("groups")
                .select(Columns.raw("*, member_count:group_members(count), tags:group_tags(tags(name))")) {
                    filter { eq("id", groupId) }
                    limit(1)
                }.decodeSingleOrNull<Group>()
        } catch (e: RestException) {
            Timber.e(e, "Repo: RestException fetching group by ID $groupId. Code: ${e.statusCode}, Message: ${e.message}")
            null
        } catch (e: Exception) {
            Timber.e(e, "Repo: Generic error fetching group by ID $groupId")
            null
        }
    }
}