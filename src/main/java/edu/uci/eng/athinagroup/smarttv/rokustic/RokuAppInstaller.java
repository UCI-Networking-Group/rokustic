/*
 * Copyright 2020 Janus Varmarken and the UCI Networking Group
 * <https://athinagroup.eng.uci.edu>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.uci.eng.athinagroup.smarttv.rokustic;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.vmichalak.protocol.ssdp.Device;
import edu.uci.eng.athinagroup.smarttv.rokustic.ecp.RokuEcpClient;
import edu.uci.eng.athinagroup.smarttv.rokustic.ecp.model.AppInfo;
import edu.uci.eng.athinagroup.smarttv.rokustic.ecp.model.RokuRemoteKey;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Installs Roku apps (channels) on a given Roku device.
 *
 * @author Janus Varmarken {@literal <jvarmark@uci.edu>}
 */
public class RokuAppInstaller {

    private final RokuEcpClient mEcpClient;

    /**
     * Create a {@code RokuAppInstaller} that will install apps on the given Roku device.
     * @param roku The Roku device on which this {@code RokuAppInstaller} will install apps.
     */
    public RokuAppInstaller(Device roku) {
        mEcpClient = new RokuEcpClient(roku);
    }

    /**
     * Install a set of apps on the Roku.
     *
     * @param appIds IDs identifying the apps to be installed.
     * @return A collection of installation reports, one for each element in {@code appIds} indicating whether that
     *         particular app was (or was not) successfully installed.
     * @throws IOException if the installation reports could not be generated because the Roku did not respond when
     *         queried for its set of installed apps. Note that this exception does not necessarily mean that the
     *         installation failed. It is possible that some (or even all) apps were successfully installed.
     */
    public List<AppInstallReport> installApps(Collection<Integer> appIds) throws IOException {
        Map<Integer, AppInstallReport> errorReports = new HashMap<>();
        for (Integer appId : appIds) {
            try {
                HttpResponse<String> response = mEcpClient.installApp(appId);

                // Give the Roku a breather (seems it sometimes stops responding when sending back to back requests)
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    // Ignore.
                }

                if (response.getStatus() != 200) {
                    // E.g., a 503 Service Unavailable response will be returned if the app is already installed, or if
                    // no app in the Roku Channel Store matches the supplied ID.
                    continue;
                }

                // Press "SELECT" to click on the "Add Channel" button that is highlighted by default when launching the
                // Channel Store page for the app.
                response = mEcpClient.sendKeypress(RokuRemoteKey.SELECT);
                if (response.getStatus() != 200) {
                    // Some unexpected result when pressing SELECT => means installation was not started. Too bad.
                    continue;
                }
                int waits = 0;
                final int maxWaits = 15;
                boolean installed;
                System.out.printf("Installing app with ID=%d...", appId);
                // Wait for the app to install (or give up after a while)
                while (!(installed = isInstalled(appId)) && waits < maxWaits) {
                    try {
                        Thread.sleep(TimeUnit.SECONDS.toMillis(5));
                    } catch (InterruptedException ie) {
                        System.err.printf("WARNING: interrupted while waiting for app with ID=%d to install.", appId);
                        System.err.println();
                    }
                    System.out.print(".");
                    waits++;
                }
                System.out.println();
                if (installed) {
                    System.out.println(String.format("Successfully installed app with ID=%d.", appId));
                } else {
                    System.out.println(
                            String.format("WARNING: timed out when attempting to install app with ID=%d.", appId));
                }
            } catch (UnirestException ue) {
                // Error occurred in one of the REST queries sent to the Roku.
                // Note that the app may actually have been successfully installed even though we observe an error,
                // namely when it is the call to isInstalled during the while loop that causes an error.
                errorReports.put(appId, new AppInstallReport(appId, false, ue));
            }
        }
        // Determine which apps were successfully installed.
        // We'll consider apps that were already installed prior to calling this method as successful installs.
        List<AppInstallReport> reports = new ArrayList<>();
        AppInfo[] installedApps;
        try {
            installedApps = mEcpClient.getInstalledApps().getBody();
        } catch (UnirestException ue) {
            // Rethrow wrapped in IOException to let caller know that we could not produce a meaningful summary of the
            // (un)successful installations since the getInstalledApps() call failed.
            throw new IOException("Installation summary unavailable: could not query installed apps.", ue);
        }
        HashSet<Integer> installedAppsIds = new HashSet<>();
        for (AppInfo appInfo : installedApps) {
            installedAppsIds.add(appInfo.getId());
        }
        for (Integer appId : appIds) {
            if (installedAppsIds.contains(appId)) {
                reports.add(new AppInstallReport(appId, true));
            } else {
                // Fetch the prebuilt error report (in case we encountered an exception above), or create one.
                AppInstallReport failureReport = errorReports.getOrDefault(appId, new AppInstallReport(appId, false));
                reports.add(failureReport);
            }
        }
        return reports;
    }

    /**
     * Query the Roku device to check if a given app is installed.
     * @param appId The ID of the app.
     * @return {@code true} if an app with an ID={@code appId} is installed on the Roku device, {@code false} otherwise.
     * @throws UnirestException if an error occurs when querying the Roku device for its installed apps.
     */
    public boolean isInstalled(int appId) throws UnirestException {
        return Arrays.stream(mEcpClient.getInstalledApps().getBody()).anyMatch(appInfo -> appInfo.getId() == appId);
    }

    /**
     * A report that indicates if a specific app was or wasn't successfully installed on a Roku device.
     */
    public static class AppInstallReport {

        private final int mAppId;
        private final boolean mInstalled;
        private final List<Exception> mErrors;

        public AppInstallReport(int appId, boolean successfullyInstalled) {
            mAppId = appId;
            mInstalled = successfullyInstalled;
            mErrors = new ArrayList<>();
        }

        public AppInstallReport(int appId, boolean successfullyInstalled, Exception... errors) {
            mAppId = appId;
            mInstalled = successfullyInstalled;
            mErrors = new ArrayList<>();
            for (Exception e : errors) {
                mErrors.add(e);
            }
        }

        public int getAppId() {
            return mAppId;
        }

        public boolean isInstalled() {
            return mInstalled;
        }

        public List<Exception> getErrors() {
            return Collections.unmodifiableList(mErrors);
        }

        public boolean addError(Exception error) {
            return mErrors.add(error);
        }
    }

}
