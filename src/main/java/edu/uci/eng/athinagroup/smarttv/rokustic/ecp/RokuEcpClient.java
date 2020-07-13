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
package edu.uci.eng.athinagroup.smarttv.rokustic.ecp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.ObjectMapper;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.vmichalak.protocol.ssdp.Device;
import edu.uci.eng.athinagroup.smarttv.rokustic.ecp.model.AppInfo;
import edu.uci.eng.athinagroup.smarttv.rokustic.ecp.model.RokuRemoteKey;

import java.io.IOException;


/**
 * Control a Roku by sending various External Command API (ECP) commands to it.
 *
 * @author Janus Varmarken {@literal <jvarmark@uci.edu>}
 */
public class RokuEcpClient {

    static {
        // The object mapper only needs to be set once, so set it at class load time
        Unirest.setObjectMapper(new ObjectMapper() {
            // Use Jackson XML instead of json mapper as Roku responses are XML, not json.
            private XmlMapper jacksonObjectMapper = new XmlMapper();

            public <T> T readValue(String value, Class<T> valueType) {
                try {
                    return jacksonObjectMapper.readValue(value, valueType);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            public String writeValue(Object value) {
                try {
                    return jacksonObjectMapper.writeValueAsString(value);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }


    /**
     * The Roku device that requests are sent to.
     */
    private final Device mRoku;

    /**
     * Create a new {@code RokuEcpClient}.
     *
     * @param targetRoku The Roku device that this client will send requests to.
     */
    public RokuEcpClient(Device targetRoku) {
        mRoku = targetRoku;
    }

    /**
     * Send the keypress identified by {@code key} to the Roku.
     *
     * @param key The key that is to be virtually pressed.
     * @return The response from the Roku device.
     * @throws UnirestException if the request fails.
     */
    public HttpResponse<String> sendKeypress(RokuRemoteKey key) throws UnirestException {
        String url = String.format("%s/keypress/%s", mRoku.getDescriptionUrl(), key.toUrlString());
        return Unirest.post(url).asString();
    }

    /**
     * Send a request to have the Roku launch the app with ID = {@code appId}.
     * @param appId The ID of the app that the Roku should launch.
     * @return The response from the Roku device.
     * @throws UnirestException if the request fails.
     */
    public HttpResponse<String> launchApp(int appId) throws UnirestException {
        String url = String.format("%s/launch/%d", mRoku.getDescriptionUrl(), appId);
        return Unirest.post(url).asString();
    }

    /**
     * Convenience method to launch a given app. See {@link #launchApp(int)}.
     * @param app The app that is to be launched.
     * @return The response from the Roku device.
     * @throws UnirestException if the request fails.
     */
    public HttpResponse<String> launchApp(AppInfo app) throws UnirestException {
        return launchApp(app.getId());
    }

    /**
     * <p>
     *     Installs an app on the Roku device. <b>NOTE: this only launches the info page for the app in the Roku Channel
     *     Store</b>. The user must press the "OK" button on that page to proceed with the installation.
     * </p>
     *
     * @param appId The ID of the app to install.
     * @return The response from the Roku device.
     * @throws UnirestException if the request fails.
     */
    public HttpResponse<String> installApp(int appId) throws UnirestException {
        String url = String.format("%s/install/%d", mRoku.getDescriptionUrl(), appId);
        return Unirest.post(url).asString();
    }

    /**
     * Query the Roku for its list of installed apps.
     * @return The response from the Roku device, which contains the list of installed apps in the message body.
     * @throws UnirestException if the request fails.
     */
    public HttpResponse<AppInfo[]> getInstalledApps() throws UnirestException {
        String url = String.format("%s/query/apps", mRoku.getDescriptionUrl());
        // Make jackson deserialize to POJO
        return Unirest.get(url).asObject(AppInfo[].class);
    }

}
