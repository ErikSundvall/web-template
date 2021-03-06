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

package care.better.platform.web.template.converter.raw.factory.leaf

import care.better.platform.template.AmNode
import care.better.platform.web.template.converter.WebTemplatePath
import care.better.platform.web.template.converter.exceptions.ConversionException
import care.better.platform.web.template.converter.raw.context.ConversionContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ValueNode
import com.marand.thinkehr.web.build.input.WebTemplateInput
import org.openehr.base.basetypes.GenericId
import org.openehr.base.basetypes.ObjectRef

/**
 * @author Primoz Delopst
 * @since 3.1.0
 *
 * Singleton instance of [RmObjectLeafNodeFactory] that creates a new instance of [ObjectRef].
 */
internal object ObjectRefFactory : RmObjectLeafNodeFactory<ObjectRef>() {

    override fun createForValueNode(
            conversionContext: ConversionContext,
            amNode: AmNode,
            valueNode: ValueNode,
            webTemplatePath: WebTemplatePath,
            webTemplateInput: WebTemplateInput?): ObjectRef =
        throw ConversionException("${amNode.rmType} can not be created from simple value", webTemplatePath.toString())

    override fun createInstance(attributes: Set<AttributeDto>): ObjectRef = ObjectRef()

    override fun handleField(
            conversionContext: ConversionContext,
            amNode: AmNode,
            attribute: AttributeDto,
            rmObject: ObjectRef,
            jsonNode: JsonNode,
            webTemplatePath: WebTemplatePath): Boolean =
        when (attribute.attribute) {
            "id" -> {
                getOrCreateId(rmObject).value = jsonNode.asText()
                true
            }
            "type" -> {
                rmObject.type = jsonNode.asText()
                true
            }
            "namespace" -> {
                rmObject.namespace = jsonNode.asText()
                true
            }
            "id_scheme" -> {
                getOrCreateId(rmObject).scheme = jsonNode.asText()
                true
            }
            else -> false
        }

    /**
     * Retrieves or creates ID from [ObjectRef].
     *
     * @param rmObject [ObjectRef]
     * @return ID as [GenericId]
     */
    private fun getOrCreateId(rmObject: ObjectRef): GenericId =
        if (rmObject.id == null)
            GenericId().also {
                it.scheme = "local"
                rmObject.id = it
            }
        else
            rmObject.id as GenericId
}
