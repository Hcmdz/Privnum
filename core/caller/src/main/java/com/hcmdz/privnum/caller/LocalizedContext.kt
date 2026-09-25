package com.hcmdz.privnum.caller

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.core.app.LocaleManagerCompat

internal fun createLocalizedContext(context: Context): Context {
    val configuration = Configuration(context.resources.configuration)
    val locales = LocaleManagerCompat.getApplicationLocales(context)
    if (!locales.isEmpty) {
        configuration.setLocales(LocaleList.forLanguageTags(locales.toLanguageTags()))
    }
    return context.createConfigurationContext(configuration)
}
