/* Copyright 2021 Better Ltd (www.better.care)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.marand.thinkehr.web.build.id.impl;

import java.util.Set;

/**
 * @author Bostjan Lah
 */
public class NumericSuffixIdDeduplicator extends AbstractSuffixIdDeduplicator {
    private static final int MAX_SUFFIX = 100;

    @Override
    protected String getUniqueSuffix(Set<String> ids, String baseId) {
        int i = 2;
        while (ids.contains(baseId + i) && i < MAX_SUFFIX) {
            i++;
        }
        return String.valueOf(i);
    }
}
