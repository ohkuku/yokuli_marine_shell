package com.yokuli.marine.data.android.location

import android.content.Context
import com.yokuli.marine.data.phone.PhoneLocationIntentStore

class SharedPreferencesPhoneLocationIntentStore(context: Context) : PhoneLocationIntentStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        "yokuli_phone_location_intent",
        Context.MODE_PRIVATE,
    )

    override suspend fun loadEnabled(): Boolean = preferences.getBoolean(ENABLED, false)

    override suspend fun saveEnabled(enabled: Boolean): Boolean =
        preferences.edit().putBoolean(ENABLED, enabled).commit()

    private companion object {
        const val ENABLED = "enabled_by_user"
    }
}
