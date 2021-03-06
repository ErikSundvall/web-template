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

package care.better.platform.web.template.converter.flat.mapper

import care.better.platform.web.template.converter.flat.context.FlatMappingContext
import care.better.platform.web.template.converter.flat.context.FormattedFlatMappingContext
import care.better.platform.web.template.converter.value.ValueConverter
import com.marand.thinkehr.web.build.WebTemplateNode
import org.openehr.rm.datatypes.DvInterval

/**
 * @author Primoz Delopst
 * @since 3.1.0
 *
 * Singleton instance of [RmObjectToFlatMapper] that maps [DvInterval] to FLAT format.
 */
internal object DvIntervalToFlatMapper : RmObjectToFlatMapper<DvInterval> {
    override fun map(
            webTemplateNode: WebTemplateNode,
            valueConverter: ValueConverter,
            rmObject: DvInterval,
            webTemplatePath: String,
            flatConversionContext: FlatMappingContext) {
        rmObject.lower?.also {
            RmObjectToFlatMapperDelegator.delegate(
                webTemplateNode.children.first { child -> child.jsonId == "lower" },
                valueConverter,
                it,
                "$webTemplatePath/lower",
                flatConversionContext)
        }
        rmObject.upper?.also {
            RmObjectToFlatMapperDelegator.delegate(
                webTemplateNode.children.first { child -> child.jsonId == "upper" },
                valueConverter,
                it,
                "$webTemplatePath/upper",
                flatConversionContext)
        }

        flatConversionContext["$webTemplatePath|lower_included"] = rmObject.lowerIncluded
        flatConversionContext["$webTemplatePath|upper_included"] = rmObject.upperIncluded
        flatConversionContext["$webTemplatePath|lower_unbounded"] = rmObject.lowerUnbounded
        flatConversionContext["$webTemplatePath|upper_unbounded"] = rmObject.upperUnbounded
    }

    override fun mapFormatted(
            webTemplateNode: WebTemplateNode,
            valueConverter: ValueConverter,
            rmObject: DvInterval,
            webTemplatePath: String,
            formattedFlatConversionContext: FormattedFlatMappingContext) {
        rmObject.lower?.also {
            RmObjectToFlatMapperDelegator.delegateFormatted(
                webTemplateNode.children.first { child -> child.jsonId == "lower" },
                valueConverter,
                it,
                "$webTemplatePath/lower",
                formattedFlatConversionContext)
        }
        rmObject.upper?.also {
            RmObjectToFlatMapperDelegator.delegateFormatted(
                webTemplateNode.children.first { child -> child.jsonId == "upper" },
                valueConverter,
                it,
                "$webTemplatePath/upper",
                formattedFlatConversionContext)
        }

        formattedFlatConversionContext["$webTemplatePath|lower_included"] = rmObject.lowerIncluded?.toString()
        formattedFlatConversionContext["$webTemplatePath|upper_included"] = rmObject.upperIncluded?.toString()
        formattedFlatConversionContext["$webTemplatePath|lower_unbounded"] = rmObject.lowerUnbounded.toString()
        formattedFlatConversionContext["$webTemplatePath|upper_unbounded"] = rmObject.upperUnbounded.toString()
    }
}
