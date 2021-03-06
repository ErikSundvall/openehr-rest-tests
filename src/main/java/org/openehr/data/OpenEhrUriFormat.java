/*
 * Copyright 2020-2021 Better Ltd (www.better.care)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.openehr.data;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Dusan Markovic
 */
public final class OpenEhrUriFormat {

    private static final String URI_PREFIX = "ehr:/";

    private OpenEhrUriFormat() {
    }

    public static String format(@Nonnull String ehrId, OpenEhrStructureType topLevelStructureType, String topLevelUid) {
        return format(new OpenEhrUri(ehrId, topLevelStructureType, topLevelUid));
    }

    public static String format(OpenEhrUri openEhrUri) {
        if (openEhrUri == null) {
            return null;
        }

        StringBuilder uriBuilder = new StringBuilder();
        uriBuilder.append(URI_PREFIX).append(openEhrUri.getEhrId());

        OpenEhrStructureType topLevelStructureType = openEhrUri.getTopLevelStructureType();
        if (topLevelStructureType != null) {
            uriBuilder.append("/").append(topLevelStructureType.getUriType());

            String topLevelUid = openEhrUri.getTopLevelUid();
            if (topLevelUid != null) {
                uriBuilder.append("/").append(topLevelUid);
            }
        }

        return uriBuilder.toString();
    }

    public static OpenEhrUri parse(String ehrUriValue) {
        // check input
        if (ehrUriValue == null) {
            return null;
        }

        if (!ehrUriValue.startsWith(URI_PREFIX)) {
            throw new IllegalArgumentException("Illegal prefix:" + ehrUriValue);
        }

        List<String> parts = Stream.of(ehrUriValue.replaceAll(URI_PREFIX, "").split("/")).peek(part -> {
            if (part.isEmpty()) {
                throw new IllegalArgumentException("Empty part: " + ehrUriValue);
            }
        }).collect(Collectors.toList());

        if (parts.size() < 1 || parts.size() > 3) {
            throw new IllegalArgumentException("Illegal number of parts: " + ehrUriValue);
        }

        // parse OpenEhr Uri
        OpenEhrStructureType structureType = parts.size() > 1 ? OpenEhrStructureType.fromUriType(parts.get(1)) : null;
        String topLevelUid = parts.size() > 2 ? parts.get(2) : null;

        return new OpenEhrUri(parts.get(0), structureType, topLevelUid);
    }
}
