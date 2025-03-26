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
import com.jamie.nodica.features.profile.ProfileViewModel
import com.jamie.nodica.features.splash.SplashViewModel
import com.jamie.nodica.supabase.provideSupabaseClient
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { provideSupabaseClient(get()) }
    single<UserGroupUseCase> { UserGroupUseCaseImpl(get()) }


    viewModel { AuthViewModel(get()) }
    viewModel { SplashViewModel(get()) }
    viewModel { ProfileViewModel(get()) }

    // Group module
    single<GroupRepository> { SupabaseGroupRepository(get()) }
    single<GroupUseCase> { GroupUseCaseImpl(get()) }


    // Bind GroupViewModel with a placeholder for currentUserId.
    viewModel {
        val client = get<SupabaseClient>()
        val userId = client.auth.currentUserOrNull()?.id
            ?: throw Exception("User not logged in")
        GroupViewModel(get(), currentUserId = userId)
    }
    viewModel {
        val client = get<SupabaseClient>()
        val userId = client.auth.currentUserOrNull()?.id
            ?: throw Exception("User not logged in")
        UserGroupsViewModel(get(), currentUserId = userId)
    }
    viewModel {
        val client = get<SupabaseClient>()
        val userId = client.auth.currentUserOrNull()?.id
            ?: throw Exception("User not logged in")
        CreateGroupViewModel(get(), currentUserId = userId)
    }

}
