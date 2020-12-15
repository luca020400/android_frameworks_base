/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.wm.shell.flicker.helpers

import android.app.Instrumentation
import android.os.SystemClock
import com.android.wm.shell.flicker.testapp.Components

class SplitScreenHelper(
    instrumentation: Instrumentation,
    activityLabel: String,
    componentsInfo: Components.ComponentsInfo
) : BaseAppHelper(
        instrumentation,
        activityLabel,
        componentsInfo
) {

    /**
     * Reopens the first device window from the list of recent apps (overview)
     */
    fun reopenAppFromOverview() {
        val x = uiDevice.displayWidth / 2
        val y = uiDevice.displayHeight / 2
        uiDevice.click(x, y)
        // Wait for animation to complete.
        SystemClock.sleep(TIMEOUT_MS)
    }

    companion object {
        const val TEST_REPETITIONS = 1
        const val TIMEOUT_MS = 3_000L
    }
}
