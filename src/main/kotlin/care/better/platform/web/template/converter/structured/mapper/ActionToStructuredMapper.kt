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

package care.better.platform.web.template.converter.structured.mapper

import care.better.platform.web.template.converter.mapper.ConversionObjectMapper
import care.better.platform.web.template.converter.mapper.putIfNotNull
import care.better.platform.web.template.converter.mapper.putSingletonAsArray
import care.better.platform.web.template.converter.value.ValueConverter
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.marand.thinkehr.web.build.WebTemplateNode
import org.openehr.rm.composition.Action

/**
 * @author Primoz Delopst
 * @since 3.1.0
 *
 * Singleton instance of [EntryToStructuredMapper] that maps [Action] to STRUCTURED format.
 */
internal object ActionToStructuredMapper : EntryToStructuredMapper<Action>() {
    override fun map(webTemplateNode: WebTemplateNode, valueConverter: ValueConverter, rmObject: Action): JsonNode =
        with(ConversionObjectMapper.createObjectNode()) {
            map(webTemplateNode, valueConverter, rmObject, this)
            mapAction(rmObject, this)
            this
        }

    override fun mapFormatted(webTemplateNode: WebTemplateNode, valueConverter: ValueConverter, rmObject: Action): JsonNode =
        with(ConversionObjectMapper.createObjectNode()) {
            mapFormatted(webTemplateNode, valueConverter, rmObject, this)
            mapAction(rmObject, this)
            this
        }

    private fun mapAction(action: Action, objectNode: ObjectNode) {
        objectNode.putSingletonAsArray("_instruction_details") {
            action.instructionDetails?.also { details ->
                val actionObjectNode = ConversionObjectMapper.createObjectNode()

                details.instructionId?.also {
                    actionObjectNode.putIfNotNull("|composition_uid", it.id?.value)
                    actionObjectNode.putIfNotNull("|path", it.path)
                }
                actionObjectNode.putIfNotNull("|activity_id", details.activityId)
                return@putSingletonAsArray actionObjectNode
            }
            return@putSingletonAsArray null
        }
    }
}