@file:Suppress("PropertyName") // Keep if needed for serialization keys

package com.jamie.nodica.features.profile_management

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jamie.nodica.features.profile.TagItem // Reusing TagItem from profile setup
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import timber.log.Timber
import java.util.UUID // For generating unique file names

// Structure to fetch user profile including related tags via the pivot table
@Serializable
data class UserProfileWithTags(
    val id: String,
    val name: String,
    val email: String? = null, // Include email if needed, fetched from auth or profile
    val school: String? = null, // Renamed from 'institution' to match ProfileSetupScreen state? Ensure consistency.
    val preferred_study_time: String? = null, // Matches DB column name
    val study_goals: String? = null, // Matches DB column name
    val profile_picture_url: String? = null, // Matches DB column name
    // This field will hold linked tag data fetched via the relation 'user_tags'
    val user_tags: List<UserTagLink> = emptyList()
)

// Represents the link in the 'user_tags' pivot table and embeds the 'tags' data
@Serializable
data class UserTagLink(
    val user_id: String,
    val tag_id: String,
    // Embeds the full TagItem object from the 'tags' table relation
    val tags: TagItem? = null
)

class ProfileManagementViewModel(
    private val supabase: SupabaseClient,
    private val currentUserId: String // Assume non-nullable, injected via Koin
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileManagementUiState())
    val uiState: StateFlow<ProfileManagementUiState> = _uiState

    init {
        fetchProfile()
    }

    fun fetchProfile() {
        _uiState.value = _uiState.value.copy(loading = true, error = null) // Start loading
        viewModelScope.launch {
            try {
                // Fetch profile data and related tags in one query using PostgREST relations
                val profileWithTags = supabase.from("users")
                    .select(Columns.raw("*, user_tags(*, tags(*))")) { // Select user, linked user_tags, and the related tag details
                        filter { eq("id", currentUserId) }
                        single() // Expect exactly one result for the current user
                    }
                    .decodeSingle<UserProfileWithTags>() // Decode into the combined data structure

                // Extract just the names of the tags the user is linked to
                val tagNames = profileWithTags.user_tags.mapNotNull { it.tags?.name }

                _uiState.value = _uiState.value.copy(
                    name = profileWithTags.name,
                    school = profileWithTags.school.orEmpty(),
                    preferredTime = profileWithTags.preferred_study_time.orEmpty(),
                    goals = profileWithTags.study_goals.orEmpty(),
                    tags = tagNames, // Update state with the list of tag names
                    profilePictureUrl = profileWithTags.profile_picture_url,
                    loading = false // Loading finished
                )
                Timber.d("Profile fetched successfully for user $currentUserId")

            } catch (e: RestException) {
                // Handle specific PostgREST errors (e.g., user not found, though unlikely if logged in)
                Timber.e(e, "RestException fetching profile for user $currentUserId: ${e.error} - ${e.message}")
                _uiState.value = _uiState.value.copy(error = "Error loading profile data: ${e.message}", loading = false)
            }
            catch (e: Exception) {
                // Catch-all for other errors (network, serialization, etc.)
                Timber.e(e, "Generic error fetching profile for user $currentUserId")
                _uiState.value = _uiState.value.copy(error = "Failed to load profile: ${e.message}", loading = false)
            }
        }
    }

    // State update functions for UI changes
    fun onNameChange(new: String) = updateState { copy(name = new) }
    fun onSchoolChange(new: String) = updateState { copy(school = new) }
    fun onTimeChange(new: String) = updateState { copy(preferredTime = new) }
    fun onGoalsChange(new: String) = updateState { copy(goals = new) }

    // Toggle tag selection in the UI state
    fun toggleTag(tag: String) = updateState {
        val currentTags = tags.toSet() // Use a Set for efficient add/remove
        copy(tags = if (tag in currentTags) (currentTags - tag).toList() else (currentTags + tag).toList())
    }

    // Upload profile picture to Supabase Storage
    fun uploadProfilePicture(context: Context, uri: Uri) { // Removed fileName param, generate it internally
        updateState { copy(loading = true, error = null) } // Indicate loading for upload
        viewModelScope.launch {
            val fileExtension = context.contentResolver.getType(uri)?.split('/')?.lastOrNull() ?: "jpg"
            val fileName = "profile_${UUID.randomUUID()}.$fileExtension" // Generate unique name
            val storagePath = "$currentUserId/$fileName" // e.g., "user_uuid/profile_xyz.jpg"

            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw Exception("Unable to open image stream from URI.")

                // Read bytes carefully, ensuring InputStream is closed
                val bytes = inputStream.use { it.readBytes() } // 'use' ensures closure

                // Upload to the 'profile_pictures' bucket
                val storageRef = supabase.storage.from("profile_pictures")
                // Call upload with path and data, then set options in lambda
                storageRef.upload(path = storagePath, data = bytes) {
                    upsert = true // Set upsert option here
                }
                Timber.d("Profile picture uploaded to: $storagePath")

                // Get the public URL of the uploaded file
                val publicUrl = storageRef.publicUrl(path = storagePath)
                Timber.d("Public URL obtained: $publicUrl")

                // Update the profile picture URL in the UI state immediately
                updateState { copy(profilePictureUrl = publicUrl, loading = false) }

                // Save the URL to the user's profile in the database
                saveProfilePictureUrl(publicUrl)

            } catch (e: Exception) {
                Timber.e(e, "Profile picture upload failed for path $storagePath")
                updateState { copy(error = "Image upload failed: ${e.message}", loading = false) }
            }
        }
    }

    // Helper function to update only the profile picture URL in the DB
    private suspend fun saveProfilePictureUrl(url: String?) {
        try {
            supabase.from("users").update(mapOf("profile_picture_url" to url)) {
                filter { eq("id", currentUserId) }
            }
            Timber.d("Profile picture URL saved to database.")
        } catch (e: Exception) {
            Timber.e(e, "Failed to save profile picture URL to database.")
            // Consider reporting this error to the UI state as well
            updateState { copy(error = "Failed to save profile picture URL: ${e.message}") }
        }
    }


    // Save all pending changes (name, school, goals, time, tags)
    fun saveChanges() {
        _uiState.value = _uiState.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                // 1. Update the core user profile fields (excluding tags)
                val profileUpdates = mapOf(
                    "name" to _uiState.value.name,
                    "school" to _uiState.value.school.ifBlank { null },
                    "preferred_study_time" to _uiState.value.preferredTime.ifBlank { null },
                    "study_goals" to _uiState.value.goals.ifBlank { null },
                    "profile_picture_url" to _uiState.value.profilePictureUrl // Ensure this is included if updated
                )
                supabase.from("users")
                    .update(profileUpdates) { filter { eq("id", currentUserId) } }
                Timber.d("User profile core fields updated.")

                // 2. Synchronize the user's tags in the 'user_tags' pivot table
                updateUserTags(currentUserId, _uiState.value.tags)
                Timber.d("User tags synchronized.")

                // 3. Refetch profile data to confirm changes and update UI state completely
                fetchProfile() // This will set loading = false upon completion

            } catch (e: Exception) {
                Timber.e(e, "Failed to save profile changes")
                updateState { copy(error = "Failed to save changes: ${e.message}", loading = false) }
            }
        }
    }

    // Logout the user
    fun logout(onComplete: () -> Unit) { // Add callback for navigation
        viewModelScope.launch {
            try {
                supabase.auth.signOut()
                Timber.d("User logged out successfully.")
                updateState { ProfileManagementUiState() } // Reset state after logout
                onComplete() // Trigger navigation after successful logout
            } catch (e: Exception) {
                Timber.e(e, "Logout failed")
                updateState { copy(error = "Logout failed: ${e.message}") }
                // Consider if onComplete should still be called or if error should block navigation
            }
        }
    }

    // Helper to update the UI state immutably
    private fun updateState(block: ProfileManagementUiState.() -> ProfileManagementUiState) {
        _uiState.value = _uiState.value.block()
    }


    /**
     * Synchronizes the user_tags relationship based on the desired list of tag names.
     * Fetches current tags, calculates diff, finds/creates tag IDs, and updates the pivot table.
     */
    private suspend fun updateUserTags(userId: String, desiredTagNames: List<String>) {
        try {
            // 1. Get current tag links for the user including the tag's name
            val currentLinks = supabase.from("user_tags")
                .select(Columns.raw("tag_id, tags!inner(name)")) { // Use !inner to ensure tag exists
                    filter { eq("user_id", userId) }
                }
                .decodeList<UserTagLink>() // Decode into list of links with embedded tag name

            val currentTagMap = currentLinks.mapNotNull { link -> link.tags?.name?.let { it to link.tag_id } }.toMap()
            val currentTagNames = currentTagMap.keys.toSet() // Use Set for efficient diff

            val desiredTagSet = desiredTagNames.toSet()

            // 2. Determine tags to add and remove
            val tagsToAdd = desiredTagSet - currentTagNames
            val tagsToRemove = currentTagNames - desiredTagSet

            Timber.d("Updating tags for user $userId: Add: $tagsToAdd, Remove: $tagsToRemove")

            // 3. Handle additions: Find or create tag, then upsert into user_tags
            if (tagsToAdd.isNotEmpty()) {
                val tagsToUpsert = tagsToAdd.mapNotNull { tagName ->
                    findOrCreateTag(tagName)?.let { tagId ->
                        mapOf("user_id" to userId, "tag_id" to tagId)
                    }
                }
                if (tagsToUpsert.isNotEmpty()) {
                    // Use the dedicated upsert function directly on the table selector
                    supabase.from("user_tags").upsert(tagsToUpsert) // <-- CORRECTED upsert call
                    // Optional: Specify conflict column if default (PK) isn't right
                    // supabase.from("user_tags").upsert(tagsToUpsert) {
                    //    onConflict = "user_id, tag_id" // If composite PK
                    // }
                    Timber.d("Upserted ${tagsToUpsert.size} tag links.")
                }
            }

            // 4. Handle removals: Delete from user_tags based on tag_id
            if (tagsToRemove.isNotEmpty()) {
                val tagIdsToRemove = tagsToRemove.mapNotNull { currentTagMap[it] }
                if (tagIdsToRemove.isNotEmpty()) {
                    supabase.from("user_tags").delete {
                        filter {
                            eq("user_id", userId)
                            isIn("tag_id", tagIdsToRemove)
                        }
                    }
                    Timber.d("Deleted ${tagIdsToRemove.size} tag links.")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error updating user tags for user $userId")
            throw e // Re-throw to be caught by the calling function (saveChanges)
        }
    }

    /**
     * Finds a tag by name (case-insensitive) or creates it if it doesn't exist.
     * Returns the tag ID or null if an error occurs.
     */
    private suspend fun findOrCreateTag(tagName: String): String? {
        if (tagName.isBlank()) return null // Avoid creating blank tags
        return try {
            // Attempt to find existing tag (case-insensitive)
            val existingTag = supabase.from("tags").select {
                filter { ilike("name", tagName) } // Use ilike for case-insensitive match
                limit(1)
            }.decodeSingleOrNull<TagItem>()

            if (existingTag != null) {
                Timber.d("Found existing tag '$tagName' with id ${existingTag.id}")
                existingTag.id
            } else {
                // Tag not found, create it (using a placeholder category for now)
                Timber.d("Tag '$tagName' not found, creating...")
                val insertedTag = supabase.from("tags").insert(
                    mapOf("name" to tagName.trim(), "category" to determineCategory(tagName)) // Trim name
                ) { select() }.decodeSingle<TagItem>() // Select to get the ID back
                Timber.d("Created new tag '$tagName' with id ${insertedTag.id}")
                insertedTag.id
            }
        } catch (e: Exception) {
            Timber.e(e, "Error finding or creating tag: $tagName")
            null // Return null on error
        }
    }

    /**
     * Dummy function to determine a tag's category based on its name.
     * Replace with actual logic if categories are managed dynamically.
     */
    private fun determineCategory(tagName: String): String {
        // Simple example logic (replace with something more robust if needed)
        return when {
            tagName.contains("Math", ignoreCase = true) -> "Mathematics"
            tagName.contains("Phys", ignoreCase = true) -> "Science"
            tagName.contains("Chem", ignoreCase = true) -> "Science"
            tagName.contains("Bio", ignoreCase = true) -> "Science"
            tagName.contains("Lang", ignoreCase = true) -> "Languages"
            tagName.contains("Eng", ignoreCase = true) -> "Languages"
            tagName.contains("Code", ignoreCase = true) -> "Coding"
            tagName.contains("Prog", ignoreCase = true) -> "Coding"
            else -> "General" // Default category
        }
    }
}