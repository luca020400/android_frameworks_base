/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.internal.os;

import android.os.BatteryStats;
import android.os.UidBatteryConsumer;
import android.util.ArrayMap;
import android.util.Log;

public class CpuPowerCalculator extends PowerCalculator {
    private static final String TAG = "CpuPowerCalculator";
    private static final boolean DEBUG = BatteryStatsHelper.DEBUG;
    private static final long MICROSEC_IN_HR = (long) 60 * 60 * 1000 * 1000;
    private final PowerProfile mProfile;

    public CpuPowerCalculator(PowerProfile profile) {
        mProfile = profile;
    }

    @Override
    protected void calculateApp(UidBatteryConsumer.Builder app, BatteryStats.Uid u,
            long rawRealtimeUs, long rawUptimeUs, int statsType) {
        long cpuTimeMs = (u.getUserCpuTimeUs(statsType) + u.getSystemCpuTimeUs(statsType)) / 1000;
        final int numClusters = mProfile.getNumCpuClusters();

        double cpuPowerMaUs = 0;
        for (int cluster = 0; cluster < numClusters; cluster++) {
            final int speedsForCluster = mProfile.getNumSpeedStepsInCpuCluster(cluster);
            for (int speed = 0; speed < speedsForCluster; speed++) {
                final long timeUs = u.getTimeAtCpuSpeed(cluster, speed, statsType);
                final double cpuSpeedStepPower = timeUs *
                        mProfile.getAveragePowerForCpuCore(cluster, speed);
                if (DEBUG) {
                    Log.d(TAG, "UID " + u.getUid() + ": CPU cluster #" + cluster + " step #"
                            + speed + " timeUs=" + timeUs + " power="
                            + formatCharge(cpuSpeedStepPower / MICROSEC_IN_HR));
                }
                cpuPowerMaUs += cpuSpeedStepPower;
            }
        }
        cpuPowerMaUs += u.getCpuActiveTime() * 1000 * mProfile.getAveragePower(
                PowerProfile.POWER_CPU_ACTIVE);
        long[] cpuClusterTimes = u.getCpuClusterTimes();
        if (cpuClusterTimes != null) {
            if (cpuClusterTimes.length == numClusters) {
                for (int i = 0; i < numClusters; i++) {
                    double power =
                            cpuClusterTimes[i] * 1000 * mProfile.getAveragePowerForCpuCluster(i);
                    cpuPowerMaUs += power;
                    if (DEBUG) {
                        Log.d(TAG, "UID " + u.getUid() + ": CPU cluster #" + i + " clusterTimeUs="
                                + cpuClusterTimes[i] + " power="
                                + formatCharge(power / MICROSEC_IN_HR));
                    }
                }
            } else {
                Log.w(TAG, "UID " + u.getUid() + " CPU cluster # mismatch: Power Profile # "
                        + numClusters + " actual # " + cpuClusterTimes.length);
            }
        }
        final double cpuPowerMah = cpuPowerMaUs / MICROSEC_IN_HR;

        if (DEBUG && (cpuTimeMs != 0 || cpuPowerMah != 0)) {
            Log.d(TAG, "UID " + u.getUid() + ": CPU time=" + cpuTimeMs + " ms power="
                    + formatCharge(cpuPowerMah));
        }

        // Keep track of the package with highest drain.
        double highestDrain = 0;
        String packageWithHighestDrain = null;
        long cpuFgTimeMs = 0;
        final ArrayMap<String, ? extends BatteryStats.Uid.Proc> processStats = u.getProcessStats();
        final int processStatsCount = processStats.size();
        for (int i = 0; i < processStatsCount; i++) {
            final BatteryStats.Uid.Proc ps = processStats.valueAt(i);
            final String processName = processStats.keyAt(i);
            cpuFgTimeMs += ps.getForegroundTime(statsType);

            final long costValue = ps.getUserTime(statsType) + ps.getSystemTime(statsType)
                    + ps.getForegroundTime(statsType);

            // Each App can have multiple packages and with multiple running processes.
            // Keep track of the package who's process has the highest drain.
            if (packageWithHighestDrain == null || packageWithHighestDrain.startsWith("*")) {
                highestDrain = costValue;
                packageWithHighestDrain = processName;
            } else if (highestDrain < costValue && !processName.startsWith("*")) {
                highestDrain = costValue;
                packageWithHighestDrain = processName;
            }
        }


        // Ensure that the CPU times make sense.
        if (cpuFgTimeMs > cpuTimeMs) {
            if (DEBUG && cpuFgTimeMs > cpuTimeMs + 10000) {
                Log.d(TAG, "WARNING! Cputime is more than 10 seconds behind Foreground time");
            }

            // Statistics may not have been gathered yet.
            cpuTimeMs = cpuFgTimeMs;
        }

        app.setConsumedPower(UidBatteryConsumer.POWER_COMPONENT_CPU, cpuPowerMah)
                .setUsageDurationMillis(UidBatteryConsumer.TIME_COMPONENT_CPU, cpuTimeMs)
                .setUsageDurationMillis(UidBatteryConsumer.TIME_COMPONENT_CPU_FOREGROUND,
                        cpuFgTimeMs)
                .setPackageWithHighestDrain(packageWithHighestDrain);
    }
}
