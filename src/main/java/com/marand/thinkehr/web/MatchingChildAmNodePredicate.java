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

package com.marand.thinkehr.web;

import care.better.platform.path.PathSegment;
import care.better.platform.template.AmNode;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * @author Primoz Delopst
 */
public class MatchingChildAmNodePredicate implements Predicate<AmNode> {
    protected final PathSegment segment;
    private final String rmType;

    public MatchingChildAmNodePredicate(PathSegment segment, String rmType) {
        this.segment = segment;
        this.rmType = rmType;
    }

    @Override
    public boolean test(AmNode amNode) {
        //noinspection OverlyComplexBooleanExpression
        return (segment.getArchetypeNodeId() == null || Objects.equals(StringUtils.trimToNull(segment.getArchetypeNodeId()),
                                                                       StringUtils.trimToNull(amNode.getArchetypeNodeId())))
                && (rmType == null || rmType.equals(amNode.getRmType()));
    }
}
