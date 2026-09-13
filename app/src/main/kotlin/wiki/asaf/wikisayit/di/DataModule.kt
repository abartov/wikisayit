package wiki.asaf.wikisayit.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import wiki.asaf.wikisayit.data.local.db.PendingUploadDao
import wiki.asaf.wikisayit.data.local.db.ProfileDao
import wiki.asaf.wikisayit.data.local.db.RecordingStatDao
import wiki.asaf.wikisayit.data.local.db.WikiSayItDatabase
import wiki.asaf.wikisayit.data.local.settings.DataStoreSettingsRepository
import wiki.asaf.wikisayit.data.local.settings.SettingsRepository
import wiki.asaf.wikisayit.data.profile.ProfileRepository
import wiki.asaf.wikisayit.data.profile.RoomProfileRepository
import wiki.asaf.wikisayit.data.stats.RoomStatsRepository
import wiki.asaf.wikisayit.data.stats.StatsRepository
import wiki.asaf.wikisayit.data.uploads.PendingUploadRepository
import wiki.asaf.wikisayit.data.uploads.RoomPendingUploadRepository
import java.time.Clock
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): WikiSayItDatabase =
        Room.databaseBuilder(context, WikiSayItDatabase::class.java, WikiSayItDatabase.DATABASE_NAME)
            .addMigrations(WikiSayItDatabase.MIGRATION_1_2, WikiSayItDatabase.MIGRATION_2_3)
            .build()

    @Provides
    fun provideProfileDao(database: WikiSayItDatabase): ProfileDao = database.profileDao()

    @Provides
    fun provideRecordingStatDao(database: WikiSayItDatabase): RecordingStatDao = database.recordingStatDao()

    @Provides
    fun providePendingUploadDao(database: WikiSayItDatabase): PendingUploadDao = database.pendingUploadDao()

    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.settingsDataStore

    @Provides
    fun provideClock(): Clock = Clock.systemUTC()
}

@Module
@InstallIn(SingletonComponent::class)
interface DataBindingsModule {
    @Binds
    fun bindProfileRepository(impl: RoomProfileRepository): ProfileRepository

    @Binds
    fun bindStatsRepository(impl: RoomStatsRepository): StatsRepository

    @Binds
    fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository

    @Binds
    fun bindPendingUploadRepository(impl: RoomPendingUploadRepository): PendingUploadRepository
}
