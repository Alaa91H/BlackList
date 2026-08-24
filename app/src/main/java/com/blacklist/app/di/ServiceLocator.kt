package com.blacklist.app.di

import android.content.Context
import androidx.room.Room
import com.blacklist.app.data.local.BlackListDatabase
import com.blacklist.app.data.repository.BlackListRepositoryImpl
import com.blacklist.app.domain.repository.BlackListRepository
import com.blacklist.app.util.ContactUtils

object ServiceLocator {
    @Volatile private var db: BlackListDatabase? = null
    @Volatile private var repo: BlackListRepository? = null
    @Volatile private var contactUtils: ContactUtils? = null

    fun provideDatabase(context: Context): BlackListDatabase =
        db ?: synchronized(this) {
            db ?: Room.databaseBuilder(context.applicationContext, BlackListDatabase::class.java, "blacklist.db")
                .fallbackToDestructiveMigrationOnDowngrade()
                .fallbackToDestructiveMigration()
                .build().also { db = it }
        }

    fun provideContactUtils(context: Context): ContactUtils =
        contactUtils ?: synchronized(this) {
            contactUtils ?: ContactUtils(context.applicationContext).also { contactUtils = it }
        }

    fun provideRepository(context: Context): BlackListRepository =
        repo ?: synchronized(this) {
            repo ?: BlackListRepositoryImpl(provideDatabase(context)).also { repo = it }
        }
}
