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
import org.openehr.rm.datatypes.DvQuantified

/**
 * @author Primoz Delopst
 * @since 3.1.0
 *
 * Implementation of [DvOrderedToFlatMapper] that maps [DvQuantified] to FLAT format.
 *
 * @constructor Creates a new instance of [DvOrderedToFlatMapper]
 */
internal abstract class DvQuantifiedToFlatMapper<T : DvQuantified> : DvOrderedToFlatMapper<T>() {
    override fun map(
            webTemplateNode: WebTemplateNode,
            valueConverter: ValueConverter,
            rmObject: T,
            webTemplatePath: String,
            flatConversionContext: FlatMappingContext) {
        super.map(webTemplateNode, valueConverter, rmObject, webTemplatePath, flatConversionContext)
        flatConversionContext["$webTemplatePath|magnitude_status"] = rmObject.magnitudeStatus
    }

    override fun mapFormatted(
            webTemplateNode: WebTemplateNode,
            valueConverter: ValueConverter,
            rmObject: T,
            webTemplatePath: String,
            formattedFlatConversionContext: FormattedFlatMappingContext) {
        super.mapFormatted(webTemplateNode, valueConverter, rmObject, webTemplatePath, formattedFlatConversionContext)
        formattedFlatConversionContext["$webTemplatePath|magnitude_status"] = rmObject.magnitudeStatus
    }
}
