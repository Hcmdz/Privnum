package com.hcmdz.privnum

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppInfo @Inject constructor() {
    val applicationId: String = BuildConfig.APPLICATION_ID
}
