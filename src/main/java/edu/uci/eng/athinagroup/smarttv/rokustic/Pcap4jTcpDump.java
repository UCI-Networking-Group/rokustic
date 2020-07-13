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

import org.pcap4j.core.*;
import org.pcap4j.packet.namednumber.DataLinkType;

import java.util.concurrent.atomic.AtomicBoolean;


/**
 * Provides capabilities for starting and stopping a packet capture from within Java. Packets are written to a pcap
 * file. Instances of this class are "use once, then throw away", i.e., once {@link #startCapture(String, String)} has
 * been called, any additional invocations of that same method will result in an error. If you wish to rerun the capture
 * for the same network interface and output to (overwrite) the same file, you should create a new {@code Pcap4jTcpDump}
 * instance.<br/><br/>
 *
 * Note that this is just a simple composition of building blocks from the amazing {@code pcap4j} library. Credit goes
 * to the author, Kaito Yamada.
 *
 * @author Janus Varmarken {@literal <jvarmark@uci.edu>}.
 */
public class Pcap4jTcpDump {

    /**
     * Handle for reading packets associated with the target network interface.
     */
    private volatile PcapHandle mNifReader;

    /**
     * Responsible for outputting packets to a file.
     */
    private volatile PcapDumper mPcapWriter;

    /**
     * Flag that indicates if the capture was (ever) started.
     */
    private AtomicBoolean mStarted = new AtomicBoolean(false);

    /**
     * Thread responsible for parsing and writing captured packets.
     */
    private final Thread mCaptureThread = new Thread(() -> {
        try {
            mNifReader.loop(-1, mPcapWriter);
        } catch (PcapNativeException|NotOpenException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            // Result of a call to breakLoop()
            // Note that it may potentially be necessary to write bogus packets to handle here in order to flush buffers
            // and have the capture terminate (see breakLoop() javadoc).
            System.out.printf("Capture marked for termination.");
        }
    });

    /**
     * Determine if the capture was started.
     * @return {@code true} if the capture was started, {@code false} otherwise. Note that this method will also
     *         return {@code true} if the capture has terminated.
     */
    public boolean isStarted() {
        return mStarted.get();
    }

    /**
     * Start capturing traffic at a given network interface and output it to a given file.
     *
     * @param nifName name of the network interface that is the target of the capture.
     * @param outputPcapFilename name of the file where the packets are to be stored.
     */
    public void startCapture(String nifName, String outputPcapFilename) throws PcapNativeException {
        // Mark as started.
        if (!mStarted.compareAndSet(false, true)) {
            throw new IllegalStateException("capture already started, can't start again");
        }

        // Get the selected nif.
        PcapNetworkInterface nif = Pcaps.getDevByName(nifName);

        // ==================== constants used below ====================
        // Max number of bytes to capture for each packet.
        final int snapshotLength = 65536;
        // The link type. We assume all uses will be working with Ethernet links.
        DataLinkType dlt = DataLinkType.EN10MB;
        // Read timeout in millis, i.e. max time the OS should buffer packets before handing them to pcap4j.
        final int readTimeout = 50;
        // ==============================================================

        // Prepare a dumper for writing the packets to a file.
        try {
            mPcapWriter = Pcaps.openDead(dlt, snapshotLength).dumpOpen(outputPcapFilename);
        } catch (NotOpenException noe) {
            // Should never happen as the handle has just been opened in the openDead call, and as there are no calls
            // inbetween openDead and dumpOpen, the handle cannot have been closed. Spare caller from having to deal
            // with the checked exception type (as it will never occur) and instead rethrow as unchecked "just in case".
            throw new RuntimeException(noe);
        }

        // Set up the handle that reads the packets.
        mNifReader = nif.openLive(snapshotLength, PcapNetworkInterface.PromiscuousMode.PROMISCUOUS, readTimeout);

        // Now start the thread that will read packets from the handle and write them to a file.
        mCaptureThread.start();
    }

    /**
     * Stop capturing traffic.
     */
    public void stopCapture() {
        if (!isStarted()) {
            throw new IllegalStateException("cannot stop a capture that was never started");
        }
        try {
            mNifReader.breakLoop();
        } catch (NotOpenException e) {
            // Should never happen, so spare caller from having to deal with the checked exception type and instead
            // rethrow as unchecked.
            throw new RuntimeException(e);
        }
    }

}
