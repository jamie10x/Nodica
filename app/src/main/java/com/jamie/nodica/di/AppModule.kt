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
import kotlinx.coroutines.FlowPreview
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import timber.log.Timber


@OptIn(FlowPreview::class)
val appModule = module {
    // --- Supabase Client ---
    // Provided once for the entire application lifecycle.
    single { provideSupabaseClient(get()) }

    // --- Repositories ---
    // Singletons as they typically hold no state specific to a feature instance.
    // They depend on the singleton SupabaseClient.
    single<GroupRepository> { SupabaseGroupRepository(supabaseClient = get()) }
    single<MessageRepository> { SupabaseMessageRepository(client = get()) }

    // --- Use Cases ---
    // Singletons as they are usually stateless intermediaries.
    // They depend on singleton Repositories.
    single<GroupUseCase> { GroupUseCaseImpl(repository = get()) }
    single<UserGroupUseCase> { UserGroupUseCaseImpl(repository = get()) }
    single<MessageUseCase> { MessageUseCaseImpl(repository = get()) }

    // --- ViewModels ---
    // Use Koin's `viewModel` scope for Android ViewModels.
    // ViewModels should fetch user ID internally when needed, not during DI setup.

    viewModel { SplashViewModel(supabase = get()) }
    viewModel { AuthViewModel(supabase = get()) }

    // ProfileViewModel for initial profile setup logic
    viewModel { ProfileViewModel(supabase = get()) }

    // ProfileManagementViewModel for editing existing profile
    viewModel {
        // Inject SupabaseClient, ViewModel will get userId from it
        ProfileManagementViewModel(supabase = get())
    }

    // UserGroupsViewModel for displaying user's joined groups
    viewModel {
        // Inject UserGroupUseCase and SupabaseClient (for userId)
        UserGroupsViewModel(userGroupUseCase = get(), supabaseClient = get())
    }

    // DiscoverGroupViewModel for discovering new groups
    viewModel {
        // Inject necessary UseCases and SupabaseClient (for userId)
        DiscoverGroupViewModel(
            groupUseCase = get(),
            userGroupUseCase = get(),
            supabaseClient = get() // Inject client
        )
    }

    // CreateGroupViewModel for the group creation form
    viewModel {
        // Inject GroupUseCase and SupabaseClient (for userId and tag fetching)
        CreateGroupViewModel(
            groupUseCase = get(),
            supabaseClient = get(),
            groupRepository = get() // Inject client
        )
    }

    // MessageViewModel requires runtime parameters (groupId)
    // currentUserId will be fetched internally now.
    viewModel { (groupId: String) -> // Parameter is just groupId
        Timber.d("Koin providing MessageViewModel for groupId: $groupId")
        MessageViewModel(
            messageUseCase = get(),
            supabaseClient = get(), // Inject client for Realtime and userId
            // Remove currentUserId parameter from injection
            groupId = groupId
        )
    }
}