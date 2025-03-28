// main/java/com/jamie/nodica/di/AppModule.kt
package com.jamie.nodica.di

import com.jamie.nodica.features.auth.AuthViewModel
import com.jamie.nodica.features.groups.group.CreateGroupViewModel
import com.jamie.nodica.features.groups.group.DiscoverGroupViewModel
import com.jamie.nodica.features.groups.group.GroupRepository
import com.jamie.nodica.features.groups.group.GroupUseCase
import com.jamie.nodica.features.groups.group.GroupUseCaseImpl
import com.jamie.nodica.features.groups.group.SupabaseGroupRepository
import com.jamie.nodica.features.groups.user_group.UserGroupUseCase
import com.jamie.nodica.features.groups.user_group.UserGroupUseCaseImpl
import com.jamie.nodica.features.groups.user_group.UserGroupsViewModel
import com.jamie.nodica.features.messages.MessageRepository
import com.jamie.nodica.features.messages.MessageUseCase
import com.jamie.nodica.features.messages.MessageUseCaseImpl
import com.jamie.nodica.features.messages.MessageViewModel
import com.jamie.nodica.features.messages.SupabaseMessageRepository
import com.jamie.nodica.features.profile.ProfileViewModel
import com.jamie.nodica.features.profile_management.ProfileManagementViewModel
import com.jamie.nodica.features.splash.SplashViewModel
import com.jamie.nodica.supabase.provideSupabaseClient
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    // Supabase Client
    single { provideSupabaseClient(get()) }

    // --- Use Cases ---
    single<GroupUseCase> { GroupUseCaseImpl(get<GroupRepository>()) }
    single<UserGroupUseCase> { UserGroupUseCaseImpl(get<GroupRepository>()) }
    single<MessageUseCase> { MessageUseCaseImpl(get<MessageRepository>()) }
    // --- Repositories ---
    single<GroupRepository> { SupabaseGroupRepository(get()) }
    single<MessageRepository> { SupabaseMessageRepository(get()) }

    // --- ViewModels ---
    viewModel { AuthViewModel(get()) }
    viewModel { SplashViewModel(get()) }
    viewModel { ProfileViewModel(get()) }

    viewModel {
        val client = get<SupabaseClient>()
        val userId = client.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("User must be logged in for Profile Management")
        ProfileManagementViewModel(get(), currentUserId = userId)
    }

    viewModel {
        val client = get<SupabaseClient>()
        val userId = client.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("User must be logged in for User Groups")
        UserGroupsViewModel(get<UserGroupUseCase>(), currentUserId = userId)
    }

    viewModel {
        val client = get<SupabaseClient>()
        val userId = client.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("User must be logged in for Discover")
        DiscoverGroupViewModel(
            groupUseCase = get<GroupUseCase>(),
            userGroupUseCase = get<UserGroupUseCase>(),
            currentUserId = userId
        )
    }

    // FIX: Inject SupabaseClient needed for tag fetching
    viewModel {
        val client = get<SupabaseClient>()
        val userId = client.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("User must be logged in to Create Group")
        CreateGroupViewModel(
            groupUseCase = get<GroupUseCase>(),
            supabaseClient = get<SupabaseClient>(), // Inject client
            currentUserId = userId
        )
    }

    viewModel { (currentUserId: String, groupId: String) ->
        MessageViewModel(
            messageUseCase = get<MessageUseCase>(),
            supabaseClient = get<SupabaseClient>(),
            currentUserId = currentUserId,
            groupId = groupId
        )
    }
}