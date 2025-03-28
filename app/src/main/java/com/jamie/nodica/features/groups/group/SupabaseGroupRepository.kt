// Corrected: main/java/com/jamie/nodica/features/groups/group/SupabaseGroupRepository.kt

package com.jamie.nodica.features.groups.group

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Count
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperation // Import needed for operators
import io.github.jan.supabase.postgrest.query.filter.FilterOperator // Import needed for operators
import kotlinx.serialization.SerialName // Import for SerialName
import kotlinx.serialization.Serializable
import timber.log.Timber

// Represents the response when fetching group memberships with nested group data
@Serializable
data class UserGroupMembershipResponse(
    // Can select specific columns if needed, but '*' for the nested group is often fine
    // Ensure the 'groups' field name matches the table name used in the foreign key relation
    val groups: Group? = null
)

// Represents the structure of the group_members table
@Serializable
data class GroupMembership(
    // Use camelCase for Kotlin properties, map to snake_case for DB with @SerialName
    @SerialName("user_id")
    val userId: String,
    @SerialName("group_id")
    val groupId: String
    // @SerialName("joined_at")
    // val joinedAt: String? = null // Can add if needed
)

class SupabaseGroupRepository(private val client: SupabaseClient) : GroupRepository {

    /**
     * Fetches groups, allowing filtering by name and tags, and excluding groups the user has already joined.
     * Populates the tags list by fetching related tags via the group_tags pivot table.
     */
    override suspend fun fetchDiscoverGroups(
        searchQuery: String,
        tagQuery: String,
        currentUserId: String // To exclude joined groups
    ): List<Group> {
        Timber.d("Fetching discover groups. Search: '$searchQuery', Tag: '$tagQuery', User: $currentUserId")
        return try {
            client.from("groups").select(
                // Fetch group columns and related tag names
                // Select group data, member count, and associated tag names
                columns = Columns.raw("*, member_count:group_members(count), tags:group_tags!inner(tags(name))")
            ) {
                // Apply filters
                if (searchQuery.isNotBlank()) {
                    // Use 'ilike' for case-insensitive partial matching on group name
                    filter {
                        ilike("name", "%${searchQuery}%")
                    }
                }
                if (tagQuery.isNotBlank()) {
                    // **FIX:** Correct syntax for filtering on related table 'tags' via 'group_tags' join
                    // Reference the joined table 'tags' and its column 'name'
                    // Use the filter block and specify the foreign table path
                    filter {
                        // The syntax often looks like foreignTable!joinTable(column).operator(value)
                        // Or directly referencing the path used in the select alias
                        // Note: Direct filtering on relation names like this might be complex/version-dependent.
                        // RPC might be more reliable for complex filtering. Trying common syntax:
                        ilike("tags.name", "%${tagQuery}%") // Use dot notation referencing the alias path
                        // Alternative if above doesn't work:
                        // foreignTable("tags") { // Specify foreign table
                        //     ilike("name", "%${tagQuery}%")
                        // }
                    }
                }

                // Local filtering for excluding joined groups is handled in ViewModel now

                order("created_at", Order.DESCENDING) // Order by creation date
                // limit(20) // Add pagination later if needed
            }.decodeList<Group>() // Decode into the Group data class

        } catch (e: Exception) {
            Timber.e(e, "Error fetching discover groups")
            // Specific error handling or re-throwing
            throw Exception("Error fetching groups: ${e.message}")
        }
    }


    /**
     * Fetches the groups a specific user is a member of.
     * Includes related tag names for each group.
     */
    override suspend fun fetchUserGroups(userId: String): List<Group> {
        Timber.d("Fetching groups for user: $userId")
        return try {
            val response = client.from("group_members").select(
                // Select the nested group ('*'), its member count, and its tags
                // Ensure 'groups' foreign key and 'group_tags' are correctly set up in Supabase
                columns = Columns.raw("groups!inner(*, member_count:group_members(count), tags:group_tags(tags(name)))")
            ) {
                filter { eq("user_id", userId) }
            }.decodeList<UserGroupMembershipResponse>() // Decode into the helper response class

            // Extract the non-null Group objects from the response
            response.mapNotNull { membership -> membership.groups }

        } catch (e: Exception) {
            Timber.e(e, "Error fetching user groups for user $userId")
            throw Exception("Error fetching user groups: ${e.message}")
        }
    }

    /**
     * Adds a user to a group by inserting into the group_members table.
     * Performs a check to prevent duplicate entries.
     */
    override suspend fun joinGroup(groupId: String, userId: String): Result<Unit> {
        Timber.d("Attempting to join group $groupId for user $userId")
        return try {
            // Check if membership already exists using count
            val existingCount = client.from("group_members")
                .select {
                    filter {
                        eq("group_id", groupId)
                        eq("user_id", userId)
                    }
                    limit(1)
                    count(Count.EXACT) // Efficiently check existence
                }.countOrNull() // Use countOrNull to handle potential errors gracefully

            if (existingCount == null || existingCount == 0L) {
                // Insert new membership using corrected GroupMembership class
                client.from("group_members")
                    .insert(GroupMembership(userId = userId, groupId = groupId)) // Use camelCase here
                Timber.i("User $userId successfully joined group $groupId")
                Result.success(Unit)
            } else {
                Timber.w("User $userId already a member of group $groupId")
                Result.failure(Exception("Already joined this group"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error joining group $groupId for user $userId")
            Result.failure(Exception("Failed to join group: ${e.message}")) // More user-friendly msg
        }
    }

    /**
     * Creates a new group and optionally adds its tags to the group_tags table.
     */
    override suspend fun createGroup(group: Group, tagIds: List<String>): Result<Group> {
        Timber.d("Creating group: Name='${group.name}', Creator='${group.creatorId}', Tags='${tagIds}'")
        return try {
            // 1. Insert the basic group information
            // Ensure Group data class doesn't try to serialize fields not in the DB table 'groups' directly
            val groupDataForInsert = mapOf(
                "name" to group.name,
                "description" to group.description,
                "meeting_schedule" to group.meetingSchedule,
                "creator_id" to group.creatorId
                // Don't include 'id', 'tags', 'members', 'createdAt' if handled by DB/separate steps
            )

            val createdGroupResult = client.from("groups")
                .insert(groupDataForInsert) { // Insert the map
                    // Select the needed columns of the newly created row
                    select(Columns.list("id", "name", "description", "meeting_schedule", "creator_id", "created_at"))
                }
                .decodeSingle<Group>() // Decode the result (Ensure Group class matches selected columns + defaults)

            Timber.i("Group created with ID: ${createdGroupResult.id}")

            // 2. Add the creator as the first member
            // Can fail if joinGroup fails, needs transaction ideally
            joinGroup(createdGroupResult.id, createdGroupResult.creatorId ?: throw IllegalStateException("Creator ID missing after group creation"))
                .onFailure { throw it } // Propagate error if creator can't join
            Timber.d("Added creator ${createdGroupResult.creatorId} to group ${createdGroupResult.id}")

            // 3. Insert entries into group_tags pivot table
            if (tagIds.isNotEmpty()) {
                val groupTagLinks = tagIds.map { tagId ->
                    mapOf("group_id" to createdGroupResult.id, "tag_id" to tagId)
                }
                client.from("group_tags").insert(groupTagLinks)
                Timber.d("Added ${groupTagLinks.size} tags to group ${createdGroupResult.id}")
            }

            // 4. Fetch the complete group data again to include tags and accurate member count
            val finalGroup = fetchGroupById(createdGroupResult.id)
                ?: throw Exception("Failed to fetch group details after creation.") // Throw error if fetch fails

            Result.success(finalGroup)

        } catch (e: Exception) {
            Timber.e(e, "Error creating group '${group.name}'")
            Result.failure(Exception("Error creating group: ${e.message}"))
        }
    }

    // Helper function to fetch details of a single group (including tags and member count)
    override suspend fun fetchGroupById(groupId: String): Group? {
        Timber.d("Fetching group by ID: $groupId")
        return try {
            client.from("groups")
                .select(Columns.raw("*, member_count:group_members(count), tags:group_tags(tags(name))")) {
                    filter { eq("id", groupId) }
                    limit(1) // Ensure only one is expected
                    // Use maybeSingle() to return null instead of throwing exception if 0 rows
                }
                .decodeSingleOrNull<Group>() // Use decodeSingleOrNull which returns null on 0 rows
        } catch (e: Exception) {
            Timber.e(e, "Error fetching group by ID $groupId")
            null // Return null on error
        }
    }
}