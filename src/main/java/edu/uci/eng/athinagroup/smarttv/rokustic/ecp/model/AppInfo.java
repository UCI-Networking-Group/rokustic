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

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;

/**
 * POJO for a single app in the list of apps returned from an ECP 'query/apps' call.
 *
 * @author Janus Varmarken {@literal <jvarmark@uci.edu>}
 */
@JacksonXmlRootElement(localName = "app")
public class AppInfo {

    @JacksonXmlProperty(isAttribute = true)
    private int id;
    @JacksonXmlProperty(isAttribute = true)
    private String subtype;
    @JacksonXmlProperty(isAttribute = true)
    private String type;
    @JacksonXmlProperty(isAttribute = true)
    private String version;
    @JacksonXmlText
    private String name;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getSubtype() {
        return subtype;
    }

    public void setSubtype(String subtype) {
        this.subtype = subtype;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return String.format("%s { id=%d; type=%s; subtype=%s; version=%s; name=%s; }",
                getClass().getSimpleName(), this.id, this.type, this.subtype, this.version, this.name);
    }

}
