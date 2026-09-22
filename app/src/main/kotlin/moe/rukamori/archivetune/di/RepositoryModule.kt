package moe.rukamori.archivetune.di

import moe.rukamori.archivetune.data.repository.AccountRepository
import moe.rukamori.archivetune.data.repository.AccountRepositoryImpl
import moe.rukamori.archivetune.data.repository.SettingsRepository
import moe.rukamori.archivetune.data.repository.SettingsRepositoryImpl
import moe.rukamori.archivetune.lyrics.LyricsTranslationUseCase
import moe.rukamori.archivetune.lyrics.LyricsTranslationUseCaseImpl
import moe.rukamori.archivetune.repository.LyricsRepository
import moe.rukamori.archivetune.repository.LyricsRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        impl: SettingsRepositoryImpl
    ): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindAccountRepository(
        impl: AccountRepositoryImpl
    ): AccountRepository

    @Binds
    @Singleton
    abstract fun bindLyricsRepository(
        impl: LyricsRepositoryImpl
    ): LyricsRepository

    @Binds
    @Singleton
    abstract fun bindLyricsTranslationUseCase(
        impl: LyricsTranslationUseCaseImpl
    ): LyricsTranslationUseCase
}