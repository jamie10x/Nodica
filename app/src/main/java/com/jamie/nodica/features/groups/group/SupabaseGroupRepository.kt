// main/java/com/jamie/nodica/features/groups/group/SupabaseGroupRepository.kt
package com.jamie.nodica.features.groups.group

import com.jamie.nodica.features.profile.TagItem // Import TagItem
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.*
import io.github.jan.supabase.supabaseJson
import kotlinx.coroutines.delay
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonObject // Import JsonObject for declaration
import timber.log.Timber
import kotlin.text.get


// Ignore warning about constructor parameter if needed:
// @Suppress("UNUSED_PARAMETER")
class SupabaseGroupRepository(
    private val supabaseClient: SupabaseClient, // Parameter used to initialize 'db'
) : GroupRepository {

    // Initialize PostgREST client from SupabaseClient
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
            Timber.e(e, "Repo: RestException fetching discover groups. Code: ${e.statusCode}, Msg: ${e.message}")
            throw Exception("Database error fetching groups: ${e.description ?: e.message}")
        } catch (e: Exception) {
            Timber.e(e, "Repo: Generic error fetching discover groups: ${e.message}")
            throw Exception("Could not fetch groups: ${e.message}")
        }
    }

    // --- REFINED fetchUserGroups ---
    override suspend fun fetchUserGroups(userId: String): List<Group> {
        Timber.d("Repo: Fetching groups for user: $userId")
        if (userId.isBlank()) {
            Timber.w("Repo: fetchUserGroups called with blank userId.")
            return emptyList()
        }

        // Refined Select Query using INNER JOIN alias for filtering
        val selectQuery = """
            *,
            member_count:group_members(count),
            tags:group_tags(tags(name)),
            user_membership:group_members!inner(user_id)
        """.trimIndent() // Alias the inner join used for filtering

        return try {
            db.from("groups")
                .select(columns = Columns.raw(selectQuery)) {
                    // Filter based on the aliased inner join result
                    filter {
                        eq("user_membership.user_id", userId)
                    }
                    order("name", Order.ASCENDING) // Order alphabetically
                }.decodeList<Group>() // Decode should ignore the extra 'user_membership' field
        } catch (e: RestException) {
            Timber.e(e, "Repo: RestException fetching user groups for user $userId. Code: ${e.statusCode}, Desc: ${e.description}, Msg: ${e.message}")
            // Check specifically for the column error again
            if (e.message?.contains("column groups.group_members does not exist", ignoreCase=true) == true ||
                e.message?.contains("column groups.user_membership does not exist", ignoreCase=true) == true ||
                e.message?.contains("missing FROM-clause entry for table", ignoreCase=true) == true ) { // Add check for join errors
                Timber.e("Repo: Query structure likely incorrect for fetching user groups. Check select/filter logic for joins.")
                throw Exception("Database query error loading your groups. Please contact support.") // More specific internal error
            } else {
                throw Exception("Database error loading your groups: ${e.description ?: e.message}")
            }
        } catch (e: HttpRequestException) {
            Timber.e(e, "Repo: Network error fetching user groups for $userId.")
            throw Exception("Network error loading your groups.")
        } catch (e: Exception) { // Catch other potential errors like Serialization
            Timber.e(e, "Repo: Generic error fetching user groups for user $userId: ${e.message}")
            throw Exception("Could not load your groups: ${e.message}")
        }
    }
    // --- END REFINED fetchUserGroups ---


    override suspend fun joinGroup(groupId: String, userId: String): Result<Unit> {
        Timber.d("Repo: User $userId attempting to join group $groupId")
        if (groupId.isBlank() || userId.isBlank()) {
            return Result.failure(IllegalArgumentException("Group ID and User ID cannot be blank."))
        }

        return try {
            val existingCount = db.from("group_members")
                .select {
                    filter {
                        eq("group_id", groupId)
                        eq("user_id", userId)
                    }
                    count(Count.EXACT)
                    limit(0)
                }
                .countOrNull()

            when {
                existingCount == null -> {
                    Timber.e("Repo: Failed to verify existing membership for user $userId in group $groupId due to count error.")
                    return Result.failure(Exception("Could not verify group membership status."))
                }
                existingCount > 0 -> {
                    Timber.w("User $userId already a member of group $groupId. Join skipped.")
                    return Result.failure(AlreadyJoinedException("You are already a member of this group."))
                }
                else -> {
                    Timber.d("User $userId is not a member of group $groupId. Proceeding with join.")
                }
            }

            val insertData = mapOf("user_id" to userId, "group_id" to groupId)
            db.from("group_members").insert(insertData)

            Timber.i("User $userId successfully joined group $groupId (or DB handled duplicate).")
            Result.success(Unit)

        } catch (e: RestException) {
            Timber.e(e, "Repo: DB RestException joining group $groupId for user $userId. Code: ${e.statusCode}, Desc: ${e.description}")
            if (e.message?.contains("duplicate key value violates unique constraint", ignoreCase = true) == true) {
                Timber.w("Repo: Duplicate key violation during join, likely already a member.")
                return Result.failure(AlreadyJoinedException("You are already a member of this group."))
            }
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
        val creatorId = group.creatorId
        Timber.d("Repo: Calling create_group_and_add_tags RPC. Name='${group.name}', Creator='$creatorId', Tags='${tagIds}'")
        if (group.name.isBlank() || creatorId.isBlank()) {
            return Result.failure(IllegalArgumentException("Group name and creator ID are required for RPC call."))
        }

        val rpcFunctionName = "create_group_and_add_tags"
        var rpcParams: JsonObject? = null

        return try {
            rpcParams = buildJsonObject {
                put("p_name", group.name)
                put("p_description", group.description.takeIf { it.isNotBlank() }) // Changed
                put("p_meeting_schedule", group.meetingSchedule?.takeIf { it.isNotBlank() }) // Changed
                put("p_creator_id", creatorId)
                put("p_tag_ids", buildJsonArray {
                    tagIds.distinct().forEach { tagId -> add(tagId) }
                })
            }
            Timber.i("Repo: Calling RPC '$rpcFunctionName' with params: $rpcParams")

            val result = db.rpc(rpcFunctionName, rpcParams)
            Timber.i("Repo: RPC '$rpcFunctionName' executed. Response data: ${result.data}")

            val resultBody = supabaseJson.parseToJsonElement(result.data)
            val newGroupId =  resultBody.jsonObject["new_group_id"]?.jsonPrimitive?.contentOrNull // Changed
                ?: throw Exception("RPC '$rpcFunctionName' did not return the new group ID in expected format. Response: ${result.data}") // Changed

            Timber.i("Repo: Group created via RPC with ID: $newGroupId.")

            val fetchDelay = 300L
            Timber.d("Repo: Adding ${fetchDelay}ms delay before fetching created group details...")
            delay(fetchDelay)

            Timber.d("Repo: Fetching full details for newly created group ID: $newGroupId")
            val finalGroup = fetchGroupById(newGroupId)
                ?: throw Exception("Failed to fetch group details (ID: $newGroupId) after RPC creation.")

            Timber.i("Repo: Successfully fetched details for created group: ${finalGroup.name}")
            Result.success(finalGroup)

        } catch (e: RestException) {
            val overloadErrorMsg = "function overloading can be resolved"
            val functionNotFoundMsg = "function $rpcFunctionName does not exist"
            when {
                e.message?.contains(overloadErrorMsg, ignoreCase = true) == true -> {
                    Timber.e(e, "Repo: RPC Overload Resolution Error for '$rpcFunctionName'. CHECK SQL FUNCTION SIGNATURE AND PARAMETER TYPES! Parameters Sent: $rpcParams")
                    Result.failure(Exception("Database error: Parameter mismatch for group creation function. Please verify database setup."))
                }
                e.message?.contains(functionNotFoundMsg, ignoreCase = true) == true -> {
                    Timber.e(e, "Repo: RPC function '$rpcFunctionName' not found in database. CHECK FUNCTION NAME AND SCHEMA!")
                    Result.failure(Exception("Database error: Group creation function not found."))
                }
                // Added this block
                e.message?.contains("violates check constraint", ignoreCase = true) == true -> {
                    Timber.e(e, "Repo: DB Check Constraint Violation calling RPC '$rpcFunctionName'. Parameters Sent: $rpcParams")
                    Result.failure(Exception("Database error: Invalid data provided for group creation. Please check your inputs."))
                }
                else -> {
                    Timber.e(e, "Repo: DB RestException calling RPC '$rpcFunctionName'. Code: ${e.statusCode}, Desc: ${e.description}, Message: ${e.message}")
                    Result.failure(Exception("Database error creating group: ${e.description ?: e.message}"))
                }
            }
        } catch (e: HttpRequestException) {
            Timber.e(e, "Repo: Network error calling RPC '$rpcFunctionName'.")
            Result.failure(Exception("Network error. Could not create group."))
        } catch (e: Exception) {
            Timber.e(e, "Repo: Generic error creating group via RPC '${group.name}': $e.message")
            Result.failure(Exception("Error creating group: $e.message"))
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
                .select(columns = Columns.raw(selectQuery)) {
                    filter { eq("id", groupId) }
                    limit(1)
                }.decodeSingleOrNull<Group>()
        } catch (e: RestException) {
            Timber.e(e, "Repo: RestException fetching group by ID $groupId. Code: ${e.statusCode}, Msg: ${e.message}")
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
            Timber.e(e, "Repo: RestException fetching all tags. Code: ${e.statusCode}, Desc: ${e.description}")
            throw Exception("Database error loading tags: ${e.description ?: e.message}")
        } catch (e: HttpRequestException) {
            Timber.e(e, "Repo: Network error fetching tags.")
            throw Exception("Network error loading tags.")
        } catch (e: Exception) {
            Timber.e(e, "Repo: Generic error fetching all tags: ${e.message}")
            throw Exception("Could not load tags: ${e.message}")
        }
    }
}