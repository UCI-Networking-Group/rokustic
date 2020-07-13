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
package edu.uci.eng.athinagroup.smarttv.rokustic.ecp.model;

/**
 * Enumeration of the keys on the standard Roku remote.
 *
 * @author Janus Varmarken {@literal <jvarmark@uci.edu>}
 * @see <a href="https://sdkdocs.roku.com/display/sdkdoc/External+Control+API#ExternalControlAPI-KeypressKeyValues">Roku ECP documentation for full list of recognized keys.</a>
 */
public enum RokuRemoteKey {

    HOME,

    REV,

    FWD,

    PLAY,

    SELECT,

    LEFT,

    RIGHT,

    DOWN,

    UP,

    BACK,

    INSTANT_REPLAY {
        @Override
        public String toUrlString() {
            return "instantreplay";
        }
    },

    INFO,

    BACKSPACE,

    SEARCH,

    ENTER;

    /**
     * Get the key name/id as it is to be used in the URL.
     * @return the key name/id as it is to be used in the URL.
     */
    public String toUrlString() {
        // For simple names (enum values whose names are single words), we can implement the key url string once as it
        // directly maps to a lowercase version of the enum value's name.
        // Multi-word names must overwrite this default version as the Java convention for enum value names does not
        // match the expected url string for the key.
        return name().toLowerCase();
    }

}
