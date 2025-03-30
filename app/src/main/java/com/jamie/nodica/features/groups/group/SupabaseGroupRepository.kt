// main/java/com/jamie/nodica/features/groups/group/SupabaseGroupRepository.kt
package com.jamie.nodica.features.groups.group

import com.jamie.nodica.features.profile.TagItem // Import TagItem
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.* // Import specific builders if needed
import io.github.jan.supabase.supabaseJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.buildJsonObject
import timber.log.Timber

class SupabaseGroupRepository(
    private val supabaseClient: SupabaseClient,
) : GroupRepository {

    private val db = supabaseClient.postgrest

    override suspend fun fetchDiscoverGroups(searchQuery: String, tagQuery: String): List<Group> {
        val cleanedSearch = searchQuery.trim()
        val cleanedTag = tagQuery.trim()
        Timber.d("Repo: Fetching discover groups. Search: '$cleanedSearch', Tag: '$cleanedTag'")
        val selectQuery = "*, member_count:group_members(count), tags:group_tags!inner(tags(name))"

        return try {
            db.from("groups").select(columns = Columns.raw(selectQuery)) {
                filter {
                    if (cleanedSearch.isNotBlank()) {
                        ilike("name", "%${cleanedSearch}%")
                    }
                    if (cleanedTag.isNotBlank()) {
                        ilike("tags.name", "%${cleanedTag}%")
                    }
                }
                order("created_at", Order.DESCENDING)
                limit(50)
            }.decodeList<Group>()
        } catch (e: RestException) {
            Timber.e(
                e,
                "Repo: RestException fetching discover groups. Code: ${e.statusCode}, Msg: ${e.message}"
            )
            throw Exception("Database error fetching groups: ${e.description ?: e.message}")
        } catch (e: Exception) {
            Timber.e(e, "Repo: Generic error fetching discover groups: ${e.message}")
            throw Exception("Could not fetch groups: ${e.message}")
        }
    }

    override suspend fun fetchUserGroups(userId: String): List<Group> {
        Timber.d("Repo: Fetching groups for user: $userId")
        if (userId.isBlank()) {
            Timber.w("Repo: fetchUserGroups called with blank userId.")
            return emptyList()
        }

        val selectQuery = "*, member_count:group_members(count), tags:group_tags(tags(name))"
        // *** Define the related table for filtering ***
        val relatedTable = "group_members"

        return try {
            db.from("groups") // Still select FROM groups
                .select(columns = Columns.raw(selectQuery)) {
                    filter {
                        // *** MODIFIED FILTER: Specify related table and column ***
                        // Use 'cs' (contains) or 'eq' on the related table directly.
                        // Requires an INNER join hint ('!inner') to ensure groups without matching members are excluded.
                        // This syntax might be more explicitly understood by PostgREST/library.
                        eq("$relatedTable.user_id", userId) // Filter WHERE group_members.user_id = userId
                        // Add '!inner' hint IF NEEDED for filtering. The select implies the join,
                        // but sometimes the filter needs the hint too. Let's try without first.
                        // Example with hint if needed: eq("$relatedTable!inner.user_id", userId)
                    }
                    order("name", Order.ASCENDING)
                }.decodeList<Group>() // Decode directly into List<Group>
        } catch (e: RestException) {
            Timber.e(
                e,
                "Repo: RestException fetching user groups for user $userId. Code: ${e.statusCode}, Desc: ${e.description}, Msg: ${e.message}"
            )
            throw Exception("Database error loading your groups: ${e.description ?: e.message}")
        } catch (e: Exception) {
            Timber.e(e, "Repo: Generic error fetching user groups for user $userId: ${e.message}")
            throw Exception("Could not load your groups: ${e.message}")
        }
    }

    override suspend fun joinGroup(groupId: String, userId: String): Result<Unit> {
        Timber.d("Repo: User $userId attempting to join group $groupId")
        if (groupId.isBlank() || userId.isBlank()) {
            return Result.failure(IllegalArgumentException("Group ID and User ID cannot be blank."))
        }

        return try {
            // 1. Check if already a member using countOrNull()
            val existingCount = db.from("group_members")
                .select {
                    filter {
                        eq("group_id", groupId)
                        eq("user_id", userId)
                    }
                    count(Count.EXACT)
                    limit(0) // Don't need rows
                }
                .countOrNull() // Extracts count from headers

            when {
                existingCount == null -> {
                    Timber.w("Repo: Could not verify existing membership for user $userId in group $groupId. Assuming not joined.")
                    // Decide whether to proceed or return an error. Proceeding might be okay if insert handles duplicates.
                }

                existingCount > 0 -> {
                    Timber.w("User $userId already a member of group $groupId")
                    return Result.failure(AlreadyJoinedException("You are already a member of this group."))
                }
            }

            // 2. Insert membership record
            val insertData = mapOf("user_id" to userId, "group_id" to groupId)
            db.from("group_members").insert(insertData)

            Timber.i("User $userId successfully joined group $groupId")
            Result.success(Unit)

        } catch (e: RestException) {
            Timber.e(
                e,
                "Repo: DB RestException joining group $groupId for user $userId. Code: ${e.statusCode}, Desc: ${e.description}"
            )
            Result.failure(Exception("Database error joining group: ${e.description ?: e.message}"))
        } catch (e: HttpRequestException) {
            Timber.e(e, "Repo: Network error joining group $groupId for user $userId.")
            Result.failure(Exception("Network error. Please try again."))
        } catch (e: Exception) {
            Timber.e(e, "Repo: Generic error joining group $groupId for user $userId: ${e.message}")
            Result.failure(Exception("Could not join group: ${e.message}"))
        }
    }

    @OptIn(SupabaseInternal::class)
    override suspend fun createGroup(group: Group, tagIds: List<String>): Result<Group> {
        Timber.d("Repo: Calling create_group_and_add_tags RPC. Name='${group.name}', Creator='${group.creatorId}', Tags='${tagIds}'")
        if (group.name.isBlank() || group.creatorId.isBlank()) {
            return Result.failure(IllegalArgumentException("Group name and creator ID are required for RPC call."))
        }

        return try {
            val rpcParams = buildJsonObject {
                put("p_name", group.name)
                put("p_description", group.description.ifBlank { null }) // Send null if blank
                put(
                    "p_meeting_schedule",
                    group.meetingSchedule?.ifBlank { null }) // Send null if blank
                put("p_creator_id", group.creatorId)
                // Assuming RPC p_tag_ids argument is of type uuid[] or text[] expecting JSON format
                put("p_tag_ids", Json.encodeToString(tagIds.distinct())) // Check RPC signature
            }
            Timber.v("Repo: RPC Params: $rpcParams")

            val result = db.rpc("create_group_and_add_tags", rpcParams)
            Timber.v("Repo: RPC Result Data: ${result.data}")

            // Adjust parsing based on expected RPC return value (e.g., the new UUID string)
            val resultBody = supabaseJson.parseToJsonElement(result.data)
            val newGroupId = resultBody.jsonPrimitive.contentOrNull // Assuming direct UUID return
                ?: resultBody.jsonObject["new_group_id"]?.jsonPrimitive?.contentOrNull // If returned in object
                ?: throw Exception("RPC 'create_group_and_add_tags' did not return the new group ID. Response: ${result.data}")

            Timber.i("Repo: Group created via RPC with ID: $newGroupId. Adding delay before fetch...")
            kotlinx.coroutines.delay(500) // Add 500ms delay - REMOVE FOR PRODUCTION LATER

            val finalGroup = fetchGroupById(newGroupId)
                ?: throw Exception("Failed to fetch group details (ID: $newGroupId) after RPC creation.")

            Result.success(finalGroup)

        } catch (e: RestException) {
            Timber.e(
                e,
                "Repo: DB RestException calling RPC. Code: ${e.statusCode}, Desc: ${e.description}"
            )
            Result.failure(Exception("Database error creating group: ${e.description ?: e.message}"))
        } catch (e: HttpRequestException) {
            Timber.e(e, "Repo: Network error calling RPC.")
            Result.failure(Exception("Network error. Could not create group."))
        } catch (e: Exception) {
            Timber.e(e, "Repo: Generic error creating group via RPC '${group.name}': ${e.message}")
            Result.failure(Exception("Error creating group: ${e.message}"))
        }
    }

    override suspend fun fetchGroupById(groupId: String): Group? {
        Timber.d("Repo: Fetching group by ID: $groupId")
        if (groupId.isBlank()) {
            Timber.w("Repo: fetchGroupById called with blank groupId.")
            return null
        }
        val selectQuery = "*, member_count:group_members(count), tags:group_tags(tags(name))"
        return try {
            db.from("groups")
                .select(Columns.raw(selectQuery)) {
                    filter { eq("id", groupId) }
                    limit(1)
                }.decodeSingleOrNull<Group>()
        } catch (e: RestException) {
            Timber.e(
                e,
                "Repo: RestException fetching group by ID $groupId. Code: ${e.statusCode}, Msg: ${e.message}"
            )
            null
        } catch (e: Exception) {
            Timber.e(e, "Repo: Generic error fetching group by ID $groupId: ${e.message}")
            null
        }
    }

    override suspend fun fetchAllTags(): List<TagItem> {
        Timber.d("Repo: Fetching all tags")
        return try {
            db.from("tags").select().decodeList<TagItem>()
        } catch (e: RestException) {
            Timber.e(
                e,
                "Repo: RestException fetching all tags. Code: ${e.statusCode}, Desc: ${e.description}"
            )
            throw Exception("Database error loading tags: ${e.description ?: e.message}")
        } catch (e: Exception) {
            Timber.e(e, "Repo: Generic error fetching all tags: ${e.message}")
            throw Exception("Could not load tags: ${e.message}")
        }
    }
}