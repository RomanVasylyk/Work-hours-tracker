package com.example.worktr.di

import android.content.Context
import com.example.worktr.data.AppDatabase
import com.example.worktr.data.DatabaseProvider
import com.example.worktr.data.JobRepository
import com.example.worktr.data.WorkEntryRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * Delegates to [DatabaseProvider] so Hilt-injected code and the
     * WorkManager workers (which are not Hilt-aware) share one instance.
     */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        DatabaseProvider.get(context)

    @Provides
    fun provideJobRepository(db: AppDatabase): JobRepository =
        JobRepository(db.jobDao())

    @Provides
    fun provideWorkEntryRepository(db: AppDatabase): WorkEntryRepository =
        WorkEntryRepository(db.workEntryDao())
}
