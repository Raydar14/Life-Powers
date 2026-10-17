package com.lifepowers.app.di

import android.content.Context
import androidx.room.Room
import com.lifepowers.app.data.DatabasePassphrase
import com.lifepowers.app.data.personal.GoalDao
import com.lifepowers.app.data.personal.HabitCheckDao
import com.lifepowers.app.data.personal.JournalDao
import com.lifepowers.app.data.personal.PersonalDatabase
import com.lifepowers.app.data.personal.TimerSessionDao
import com.lifepowers.app.data.personal.WorldEventDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PersonalDatabaseModule {

    @Provides
    @Singleton
    fun personalDatabase(@ApplicationContext context: Context): PersonalDatabase {
        System.loadLibrary("sqlcipher")
        val passphrase = DatabasePassphrase.getOrCreate(context)
        return Room.databaseBuilder(context, PersonalDatabase::class.java, PersonalDatabase.NAME)
            .openHelperFactory(SupportOpenHelperFactory(passphrase))
            .build()
    }

    @Provides fun goalDao(db: PersonalDatabase): GoalDao = db.goalDao()
    @Provides fun habitCheckDao(db: PersonalDatabase): HabitCheckDao = db.habitCheckDao()
    @Provides fun timerSessionDao(db: PersonalDatabase): TimerSessionDao = db.timerSessionDao()
    @Provides fun journalDao(db: PersonalDatabase): JournalDao = db.journalDao()
    @Provides fun worldEventDao(db: PersonalDatabase): WorldEventDao = db.worldEventDao()
}
