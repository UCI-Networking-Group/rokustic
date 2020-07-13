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

import java.io.*;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Exercises a Roku app by performing a default set of key presses on a virtual Roku remote.
 *
 * @author Janus Varmarken {@literal <jvarmark@uci.edu>}
 */
public class DefaultRokuAppExerciser implements Runnable {

    private final RokuEcpClient mEcpClient;
    private final AppInfo mApp;
    private final File mBaseDir;

    /**
     * Name of network interface that is the target of the packet capture.
     * Defaults to {@code "wlan0"}.
     */
    private volatile String mNifName = "wlan0";

    private final Pcap4jTcpDump mPacketCapture = new Pcap4jTcpDump();

    /**
     * Create a new {@code DefaultRokuAppExerciser} that will exercise the app identified by the provided
     * {@link AppInfo} on the Roku identified by the provided {@link Device}, and that will output data (e.g., error
     * logs) to a specified base directory.
     * @param roku The target Roku where the app is to be exercised. It is assumed that the app is already installed on
     *             this Roku.
     * @param targetApp The app that is to be exercised.
     * @param baseDir A directory where output is to be written.
     */
    public DefaultRokuAppExerciser(Device roku, AppInfo targetApp, String baseDir) {
        mEcpClient = new RokuEcpClient(roku);
        mApp = targetApp;
        mBaseDir = new File(baseDir);
        // Ensure validity of base dir, creating dir if necessary.
        if (mBaseDir.isFile()) {
            throw new RuntimeException("specified base dir is a file");
        }
        if (!mBaseDir.exists()) {
            if (!mBaseDir.mkdirs()) {
                throw new RuntimeException("could not create base dir for storing output");
            }
        }
    }

    @Override
    @SuppressWarnings("Duplicates")
    public void run() {
        try {
            mPacketCapture.startCapture(mNifName, pcapFilepath().getAbsolutePath());
            // Wrap status code check in consumer to avoid redoing if-check for all ecp api calls.
            final int expectedStatusCode = 200;
            BiConsumer<HttpResponse<?>, Optional<String>> httpLogger = (httpResponse, note) -> {
                if (httpResponse.getStatus() != expectedStatusCode) {
                    logHttpResponse(httpResponse, note);
                }
            };
            HttpResponse<?> resp;
            // Launch the target app.
            resp = mEcpClient.launchApp(mApp);
            httpLogger.accept(resp, Optional.of("launch #1."));
            // Allow the app time to load.
            Thread.sleep(secondsToMillis(20));
            // Send "OK" keypress (common that initially selected UI element is the next recommended/featured video)
            resp = mEcpClient.sendKeypress(RokuRemoteKey.SELECT);
            httpLogger.accept(resp, Optional.of(RokuRemoteKey.SELECT.name() + " #1"));
            // Let video play for a couple of minutes
            Thread.sleep(minutesToMillis(5));

            // Relaunch the app s.t. we can play different content.
            List<HttpResponse<?>> relaunchRespones = performRelaunchAppControlSequence();
            relaunchRespones.forEach(r -> httpLogger.accept(r, Optional.of("part of relaunch #1 keypress sequence")));
            /*
             * Try using the arrow keys a bit to find a video that is not the default/recommended one.
             * Note: avoid going (too much) left, as some apps (e.g. YouTube) have a tab-like menu-bar on the left,
             * and we want to play content, not randomly change settings.
             */
            resp = mEcpClient.sendKeypress(RokuRemoteKey.DOWN);
            httpLogger.accept(resp, Optional.of(RokuRemoteKey.DOWN.name() + " #1"));
            Thread.sleep(secondsToMillis(1));
            resp = mEcpClient.sendKeypress(RokuRemoteKey.DOWN);
            httpLogger.accept(resp, Optional.of(RokuRemoteKey.DOWN.name() + " #2"));
            Thread.sleep(secondsToMillis(1));
            resp = mEcpClient.sendKeypress(RokuRemoteKey.RIGHT);
            httpLogger.accept(resp, Optional.of(RokuRemoteKey.RIGHT.name() + " #1"));
            Thread.sleep(secondsToMillis(1));
            resp = mEcpClient.sendKeypress(RokuRemoteKey.RIGHT);
            httpLogger.accept(resp, Optional.of(RokuRemoteKey.RIGHT.name() + " #2"));
            Thread.sleep(secondsToMillis(1));
            // Play whatever video we've ended up at.
            resp = mEcpClient.sendKeypress(RokuRemoteKey.SELECT);
            httpLogger.accept(resp, Optional.of(RokuRemoteKey.SELECT.name() + " #2"));
            // Let video play for a couple of minutes.
            Thread.sleep(minutesToMillis(5));

            // Relaunch the app s.t. we can play different content.
            relaunchRespones = performRelaunchAppControlSequence();
            relaunchRespones.forEach(r -> httpLogger.accept(r, Optional.of("part of relaunch #2 keypress sequence")));
            // Use the arrow keys to navigate to the thumbnail of a 3rd video.
            resp = mEcpClient.sendKeypress(RokuRemoteKey.DOWN);
            httpLogger.accept(resp, Optional.of(RokuRemoteKey.DOWN.name() + " #3"));
            Thread.sleep(secondsToMillis(1));
            resp = mEcpClient.sendKeypress(RokuRemoteKey.DOWN);
            httpLogger.accept(resp, Optional.of(RokuRemoteKey.DOWN.name() + " #4"));
            Thread.sleep(secondsToMillis(1));
            resp = mEcpClient.sendKeypress(RokuRemoteKey.SELECT);
            httpLogger.accept(resp, Optional.of(RokuRemoteKey.SELECT.name() + " #3"));
            // Let the video play for a couple of minutes.
            Thread.sleep(minutesToMillis(5));
            // Quit the app.
            resp = mEcpClient.sendKeypress(RokuRemoteKey.HOME);
            httpLogger.accept(resp, Optional.of("Quit (go to Roku home screen)"));
            // Wait a bit for the Roku to load the device home screen.
            Thread.sleep(secondsToMillis(10));
        } catch (Exception e) {
            // If something fails, we discard the run for the sake of consistency.
            // Print the stacktrace for immediate feedback.
            e.printStackTrace();
            // Dump the error information to a file to make it clear that the experiment must be rerun and to enable
            // subsequent investigation of the error.
            logError(e);
        } finally {
            // Terminate packet capture.
            if (mPacketCapture.isStarted()) {
                mPacketCapture.stopCapture();
            }
        }
    }

    /**
     * Set the name of the network interface that should be monitored while the Roku app is being exercised.
     * @param nifName the name of the network interface that should be monitored while the Roku app is being exercised.
     */
    public void setNifName(String nifName) {
        mNifName = nifName;
    }

    /**
     * If an error occurs during {@link #run()}, the full sequence of virtual key presses has not been performed, and
     * {@link #mApp} has therefore not been fully exercised. If one exercises many apps in one batch (e.g., over night),
     * merely printing errors to {@code System.err} is an impractical approach as the error output will get mixed with
     * (a lot of) other output and will therefore most likely not be noticed. To enable identification of apps that
     * needs to be "re-exercised", and to aid debugging, this method prints the error information to a file, which
     * embeds {@link #mApp}'s ID in its name.
     *
     * @param error The error that occurred during {@link #run()}.
     */
    private void logError(Exception error) {
        try {
            BufferedWriter fileWriter = new BufferedWriter(new FileWriter(errLogFilepath()));
            PrintWriter printWriter = new PrintWriter(fileWriter);
            // Print metadata as header.
            String header = String.format("Error report for '%s' version '%s' (app id: %d)", mApp.getName(), mApp.getVersion(), mApp.getId());
            printWriter.println(header);
            printWriter.println();
            // Print the exception message and stack trace
            printWriter.println(error.getMessage());
            printWriter.println();
            error.printStackTrace(printWriter);
            // Flush streams and clean up.
            printWriter.flush();
            fileWriter.flush();
            printWriter.close();
            fileWriter.close();
        } catch (IOException ioe) {
            // Too bad: error while writing error info.
            // Not too much we can do about this except provide immediate feedback.
            ioe.printStackTrace();
        }
    }

    /**
     * Logs an HTTP response by appending it to the file named by {@link #httpErrLogFilepath()}.
     * @param resp The HTTP response to log.
     * @param note A note to be associated with the response.
     */
    private void logHttpResponse(HttpResponse<?> resp, Optional<String> note) {
        try {
            boolean created = httpErrLogFilepath().createNewFile();
            // Open in append mode as we may call this multiple times (if more than one roku ecp request return an error code)
            BufferedWriter fileWriter = new BufferedWriter(new FileWriter(httpErrLogFilepath(), true));
            PrintWriter printWriter = new PrintWriter(fileWriter);
            // Print a header line if this is the first http error we are writing
            if (created) {
                String header = String.format("HTTP error report for '%s' version '%s' (app id: %d)", mApp.getName(), mApp.getVersion(), mApp.getId());
                printWriter.println(header);
            }
            // Blank line between each response.
            printWriter.println();
            // Wrap all content for a single response within a set of horizontal lines.
            printWriter.println("=========================================================");
            // Print metadata.
            printWriter.println("Logged: " + ZonedDateTime.now().toString());
            note.ifPresent(n -> printWriter.println(String.format("Note: %s", n)));
            // Blank line before response data.
            printWriter.println();
            // Write HTTP status line.
            printWriter.println(String.format("%d %s", resp.getStatus(), resp.getStatusText()));
            // Write headers.
            for (Map.Entry<String, List<String>> h : resp.getHeaders().entrySet()) {
                // Print header name. Don't skip to next line as values are to follow.
                printWriter.printf("%s:", h.getKey());
                // Print header value(s)
                for (int i = 0; i < h.getValue().size(); i++) {
                    printWriter.printf(" %s", h.getValue().get(i));
                    if (i < h.getValue().size() - 1) {
                        // Print value separator for all but the last value.
                        printWriter.print(",");
                    }
                }
                // Skip line as next header goes on next line.
                printWriter.println();
            }
            // Blank line before message body.
            printWriter.println();
            // Next print the message body, treating every data type as a string
            // (assumes utf-8; may be improved by reading the content type)
            String encoding = "UTF-8";
            InputStream msgBodyStream = resp.getRawBody();
            byte[] buf = new byte[4096];
            StringBuilder sb = new StringBuilder();
            int bytesRead;
            while ((bytesRead = msgBodyStream.read(buf)) != -1) {
                sb.append(new String(buf, 0, bytesRead, encoding));
            }
            printWriter.println("=========================================================");
            // Flush streams and clean up.
            msgBodyStream.close();
            printWriter.flush();
            fileWriter.flush();
            printWriter.close();
            fileWriter.close();
        } catch (IOException ioe) {
            // Too bad: error while writing error info.
            // Not too much we can do about this except provide immediate feedback.
            ioe.printStackTrace();
        }
    }

    /**
     * Invoke a sequence of key presses that takes the user to the home screen, and then re-launches {@link #mApp}.
     *
     * @return The responses received from the Roku, one for each keypress.
     * @throws UnirestException if an ECP request fails.
     * @throws InterruptedException if some naughty thread decides to interrupt the current thread's beauty sleep.
     */
    private List<HttpResponse<?>> performRelaunchAppControlSequence() throws UnirestException, InterruptedException {
        // Bundle responses in list to allow calling code access to them, e.g., for logging.
        ArrayList<HttpResponse<?>> responses = new ArrayList<>();
        responses.add(mEcpClient.sendKeypress(RokuRemoteKey.HOME));
        // Let the homescreen load
        Thread.sleep(secondsToMillis(10));
        // Then relaunch the app.
        responses.add(mEcpClient.launchApp(mApp));
        // Allow the app time to load.
        Thread.sleep(secondsToMillis(20));
        return responses;
    }

    private long minutesToMillis(long minutes) {
        return minutes * secondsToMillis(60);
    }

    private long secondsToMillis(long seconds) {
        return seconds * 1_000;
    }

    /**
     * Generate a suitable name for the pcap file, i.e., one that embeds {@link #mApp}'s id.
     * The pcap file is stored in the {@link #mBaseDir}. The returned {@link File} instance is set up such that it
     * represents the absolute path to the pcap file.
     *
     * @return a {@link File} representation of the pcap file.
     */
    private File pcapFilepath() {
        String pcapFilename = String.format("app-%d.pcap", mApp.getId());
        return mBaseDir.toPath().resolve(pcapFilename).toFile();
    }

    /**
     * Similar to {@link #pcapFilepath()}, but for the error log.
     * @return a {@link File} representation of the error log file.
     */
    private File errLogFilepath() {
        String errReportFilename = String.format("app-%d-error-report.txt", mApp.getId());
        return mBaseDir.toPath().resolve(errReportFilename).toFile();
    }

    private File httpErrLogFilepath() {
        String httpErrReportFilename = String.format("app-%d-http-error-report.txt", mApp.getId());
        return mBaseDir.toPath().resolve(httpErrReportFilename).toFile();
    }

}
