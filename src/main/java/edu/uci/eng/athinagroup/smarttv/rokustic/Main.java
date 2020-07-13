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
import com.vmichalak.protocol.ssdp.SSDPClient;
import edu.uci.eng.athinagroup.smarttv.rokustic.ecp.RokuEcpClient;
import edu.uci.eng.athinagroup.smarttv.rokustic.ecp.model.AppInfo;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Application main entry point.
 *
 * @author Janus Varmarken {@literal <jvarmark@uci.edu>}
 */
public class Main {

    /**
     * How long to perform SSDP discovery, in milliseconds.
     */
    public static final int DISCOVERY_TIMEOUT_MILLIS = 10_000;

    private static final Map<Integer, Task> TASKS;

    static {
        TASKS = new HashMap<>();
        int taskId = 0;
        Task installAppsTask = new Task(++taskId, "Install Roku apps.", Main::installApps);
        TASKS.put(installAppsTask.getId(), installAppsTask);
        Task exerciseAppsTask = new Task(++taskId, "Exercise Roku apps.", Main::exerciseApps);
        TASKS.put(exerciseAppsTask.getId(), exerciseAppsTask);
    }

    public static void main(String[] args) {
        Device roku;
        try {
            roku = selectDevice();
        } catch (IOException ioe) {
            System.err.println("Roku device discovery failed. Terminating.");
            return;
        }
        if (roku == null) {
            System.out.println("No Rokus found on the local network. Terminating.");
            return;
        }
        System.out.println("[ Selected Roku: " + roku + " ]");
        Task task = selectTask();
        task.execute(roku);
    }

    /**
     * Asks the user to select a task to be performed, and returns the selected task.
     * @return The task selected by the user.
     */
    private static Task selectTask() {
        Scanner stdIn = new Scanner(System.in);
        System.out.println("What would you like to do? Please select the corresponding task number below.");

        List<Integer> sortedIds = new ArrayList<>(TASKS.keySet());
        Collections.sort(sortedIds);

        for (Integer taskId : sortedIds) {
            System.out.printf("%d: %s", taskId, TASKS.get(taskId).getDescription());
            System.out.println();
        }

        while(true) {
            try {
                String line = stdIn.nextLine();
                String[] tokens = line.split(" ");
                if (tokens.length > 0) {
                    // Assume task number is first token; allow garbage after chosen task.
                    int taskId = Integer.parseInt(tokens[0]);
                    if (TASKS.containsKey(taskId)) {
                        // Valid task number;
                        return TASKS.get(taskId);
                    }
                }
            } catch (NumberFormatException nfe) { }
            System.out.println("Invalid task number, please try again.");
        }
    }

    /**
     * Performs SSDP discovery for Rokus and prompts the user to select a discovered device. <br/>
     * Structure of SSDP request that Rokus will respond to:
     *
     * <pre>
     * M-SEARCH * HTTP/1.1
     * Host: 239.255.255.250:1900
     * Man: "ssdp:discover"
     * ST: roku:ecp
     * </pre>
     *
     * @return A {@link Device} instance that wraps metadata for the chosen device, or {@code null} if no Rokus were
     *         discovered.
     * @throws IOException if SSDP discovery fails.
     */
    public static Device selectDevice() throws IOException {
        System.out.println(String.format("Initiating SSDP discovery of Rokus. Will timeout after %d ms.", DISCOVERY_TIMEOUT_MILLIS));
        List<Device> discoveredRokus = SSDPClient.discover(DISCOVERY_TIMEOUT_MILLIS, "roku:ecp");
        if (discoveredRokus.size() == 0) {
            return null;
        }
        System.out.println(String.format("Discovered %d Roku device(s):", discoveredRokus.size()));
        for (int i = 0; i < discoveredRokus.size(); i++) {
            System.out.println(String.format("  %d: %s", i+1, discoveredRokus.get(i)));
        }
        System.out.println("Please select the target device by its index.");
        Scanner stdIn = new Scanner(System.in);
        int rokuIdx;
        while (true) {
            try {
                String line = stdIn.nextLine();
                String[] tokens = line.split(" ");
                if (tokens.length > 0) {
                    // Assume index is first token; allow garbage after chosen index.
                    rokuIdx = Integer.parseInt(tokens[0]) - 1;
                    if (rokuIdx >= 0 && rokuIdx < discoveredRokus.size()) {
                        // Valid index.
                        break;
                    }
                }
            } catch (NumberFormatException nfe) { }
            System.out.println("Invalid index, please try again.");
        }
        return discoveredRokus.get(rokuIdx);
    }

    private static void installApps(Device roku) {
        System.out.println("Type the path to the file that defines what channels to install, then press enter.");
        Scanner stdIn = new Scanner(System.in);
        Set<Integer> appIds;
        while (true) {
            String line = stdIn.nextLine();
            File f = new File(line);
            Path p = f.toPath();
            if (!Files.isReadable(p)) {
                System.out.println("Invalid file path, or unreadable file. Please try again.");
                continue;
            }
            List<String> lines;
            try {
                lines = Files.readAllLines(p);
            } catch (IOException ioe) {
                System.out.println("Error occurred while reading specified file. Please try again.");
                continue;
            }
            // Assume a single app ID in each line.
            appIds = new HashSet<>();
            for (String ln : lines) {
                if (ln.trim().startsWith("#")) {
                    // Interpret pound sign as comment
                    continue;
                }
                try {
                    appIds.add(Integer.parseInt(ln));
                } catch (NumberFormatException nfe) {
                    System.out.println("WARNING: line could not be parsed as a channel ID: '" + line + "'.");
                }
            }
            if (appIds.size() == 0) {
                System.out.println("Provided file did not contain any channel IDs or was in an incorrect format.");
                continue;
            } else {
                System.out.printf("Successfully read %d channel IDs from file.", appIds.size());
                System.out.println();
                break;
            }
        }
        RokuAppInstaller rokuAppInstaller = new RokuAppInstaller(roku);
        try {
            List<RokuAppInstaller.AppInstallReport> reports = rokuAppInstaller.installApps(appIds);
            long successes = reports.stream().filter(r -> r.isInstalled()).count();
            long failures = reports.stream().filter(r -> !r.isInstalled()).count();
            System.out.printf("Successfully installed %d apps.", successes);
            System.out.println();
            if (failures > 0) {
                System.out.printf("Installation failed for %d apps.", failures);
                System.out.println();
                StringBuilder sb = new StringBuilder();
                sb.append("Installation failed for apps with IDs: ");
                List<Integer> failedInstalls = reports.stream().
                        filter(r -> !r.isInstalled()).map(r -> r.getAppId()).collect(Collectors.toList());
                for (int i = 0; i < failedInstalls.size(); i++) {
                    Integer appId = failedInstalls.get(i);
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(appId);
                }
                System.out.println(sb.toString());
            }
        } catch (IOException e) {
            System.out.println("Installation reports could not be produced. Some apps may not have been installed.");
        }
    }

    private static void exerciseApps(Device roku) {
        Scanner stdIn = new Scanner(System.in);
        System.out.println("Enter the name of the network interface to record traffic on, then press enter.");
        String nif = stdIn.nextLine().trim();
        System.out.println("Enter the path to the output directory, then press enter.");
        String baseDir = stdIn.nextLine().trim();

        RokuEcpClient rokuEcpClient = new RokuEcpClient(roku);
        // Get the set of apps installed on the Roku.
        HttpResponse<AppInfo[]> appsResponse;
        try {
            appsResponse = rokuEcpClient.getInstalledApps();
        } catch (UnirestException ue) {
            System.out.println("ERROR: Could not get list of installed apps. Terminating.");
            return;
        }
        if (appsResponse.getStatus() == 200) {
            System.out.printf("Found %d apps on %s.", appsResponse.getBody().length, roku.getDescriptionUrl());
            System.out.println();
            // Exercise each installed app.
            for(AppInfo app : appsResponse.getBody()) {
                if (app.getId() == 31012) {
                    // Hack to exclude https://channelstore.roku.com/details/31012/fandangonow
                    // This app is hardwired on the Roku - there is no way to remove it.
                    continue;
                }
                System.out.printf("Exercising %s...", app.getName());
                System.out.println();
                DefaultRokuAppExerciser appExerciser = new DefaultRokuAppExerciser(roku, app, baseDir);
                appExerciser.setNifName(nif);
                appExerciser.run();
                System.out.printf("Done exercising %s.", app.getName());
                System.out.println();
            }
        } else {
            System.out.println("ERROR: Query for installed apps failed. Terminating.");
            return;
        }
    }

    private static class Task {

        private final int mId;
        private final String mDescription;
        private final Consumer<Device> mJob;

        private Task(int id, String description, Consumer<Device> job) {
            mId = id;
            mDescription = description;
            mJob = job;
        }

        private int getId() {
            return mId;
        }

        private String getDescription() {
            return mDescription;
        }

        private void execute(Device roku) {
            mJob.accept(roku);
        }

    }

}
