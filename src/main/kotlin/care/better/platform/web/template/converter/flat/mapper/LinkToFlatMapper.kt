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
import org.openehr.rm.common.Link

/**
 * @author Primoz Delopst
 * @since 3.1.0
 *
 * Singleton instance of [RmObjectToFlatMapper] that maps [Link] to FLAT format.
 */
internal object LinkToFlatMapper : RmObjectToFlatMapper<Link> {
    override fun map(
            webTemplateNode: WebTemplateNode,
            valueConverter: ValueConverter,
            rmObject: Link,
            webTemplatePath: String,
            flatConversionContext: FlatMappingContext) {
        flatConversionContext["$webTemplatePath|meaning"] = rmObject.meaning?.value
        flatConversionContext["$webTemplatePath|type"] = rmObject.type?.value
        flatConversionContext["$webTemplatePath|target"] = rmObject.target?.value
    }

    override fun mapFormatted(
            webTemplateNode: WebTemplateNode,
            valueConverter: ValueConverter,
            rmObject: Link,
            webTemplatePath: String,
            formattedFlatConversionContext: FormattedFlatMappingContext) {
        formattedFlatConversionContext["$webTemplatePath|meaning"] = rmObject.meaning?.value
        formattedFlatConversionContext["$webTemplatePath|type"] = rmObject.type?.value
        formattedFlatConversionContext["$webTemplatePath|target"] = rmObject.target?.value
    }
}
