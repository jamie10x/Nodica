package com.jamie.nodica.di

import com.jamie.nodica.features.auth.AuthViewModel
import com.jamie.nodica.features.groups.group.GroupRepository
import com.jamie.nodica.features.groups.group.GroupUseCase
import com.jamie.nodica.features.groups.group.GroupUseCaseImpl
import com.jamie.nodica.features.groups.group.GroupViewModel
import com.jamie.nodica.features.groups.group.SupabaseGroupRepository
import com.jamie.nodica.features.groups.user_group.UserGroupsViewModel
import com.jamie.nodica.features.groups.group.CreateGroupViewModel
import com.jamie.nodica.features.groups.user_group.UserGroupUseCase
import com.jamie.nodica.features.groups.user_group.UserGroupUseCaseImpl
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

    // Auth & Core Profile
    viewModel { AuthViewModel(get()) }
    viewModel { SplashViewModel(get()) }
    viewModel { ProfileViewModel(get()) } // For initial profile setup

    // Profile Management (Logged-in users only)
    viewModel {
        val client = get<SupabaseClient>()
        val userId = client.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("User must be logged in to access Profile Management")
        ProfileManagementViewModel(get(), currentUserId = userId)
    }

    // User Groups (Groups the user is a member of)
    single<UserGroupUseCase> { UserGroupUseCaseImpl(get()) }
    viewModel {
        val client = get<SupabaseClient>()
        val userId = client.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("User must be logged in to access User Groups")
        UserGroupsViewModel(get(), currentUserId = userId)
    }

    // Group Discovery & General Group Operations
    single<GroupRepository> { SupabaseGroupRepository(get()) }
    single<GroupUseCase> { GroupUseCaseImpl(get()) }
    viewModel {
        val client = get<SupabaseClient>()
        val userId = client.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("User must be logged in to access Group features") // Needed for join actions
        GroupViewModel(get(), currentUserId = userId)
    }

    // Create Group
    viewModel {
        val client = get<SupabaseClient>()
        val userId = client.auth.currentUserOrNull()?.id
            ?: throw IllegalStateException("User must be logged in to create a Group")
        CreateGroupViewModel(get(), currentUserId = userId)
    }

    // Messages
    single<MessageRepository> { SupabaseMessageRepository(get()) }
    single<MessageUseCase> { MessageUseCaseImpl(get()) }
    viewModel { (currentUserId: String, groupId: String) -> // For dynamic parameters via parametersOf()
        MessageViewModel(
            messageUseCase = get(),
            supabaseClient = provideSupabaseClient(get()),
            currentUserId = currentUserId,
            groupId = groupId
        )
    }
}