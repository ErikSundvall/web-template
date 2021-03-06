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

package care.better.platform.web.template.converter.raw

import care.better.openehr.rm.RmObject
import care.better.platform.template.AmAttribute
import care.better.platform.template.AmNode
import care.better.platform.utils.RmUtils
import care.better.platform.web.template.converter.WebTemplatePath
import care.better.platform.web.template.converter.exceptions.ConversionException
import care.better.platform.web.template.converter.mapper.ConversionObjectMapper
import care.better.platform.web.template.converter.mapper.getObjectNodeForWebTemplatePath
import care.better.platform.web.template.converter.mapper.getObjectNodeForWebTemplateSegment
import care.better.platform.web.template.converter.mapper.isEmptyInDepth
import care.better.platform.web.template.converter.raw.context.ConversionContext
import care.better.platform.web.template.converter.raw.context.ConversionContextExtractor
import care.better.platform.web.template.converter.raw.extensions.*
import care.better.platform.web.template.converter.raw.factory.leaf.RmObjectLeafNodeFactoryProvider
import care.better.platform.web.template.converter.raw.factory.node.RmObjectNodeFactoryProvider
import care.better.platform.web.template.converter.raw.generics.GenericFieldExtractor
import care.better.platform.web.template.converter.raw.postprocessor.PostProcessDelegator
import care.better.platform.web.template.converter.raw.special.SpecialCaseRmObjectHandlerProvider
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.marand.thinkehr.web.build.WebTemplateInputType
import com.marand.thinkehr.web.build.WebTemplateNode
import com.marand.thinkehr.web.build.input.WebTemplateInput
import org.openehr.base.basetypes.TemplateId
import org.openehr.rm.common.Archetyped
import org.openehr.rm.common.Locatable
import org.openehr.rm.composition.Composition
import org.openehr.rm.datastructures.Element
import org.openehr.rm.datatypes.DataValue
import java.util.*

/**
 * @author Primoz Delopst
 * @since 3.1.0
 *
 * Converts the RM object in STRUCTURED format to the RM object in RAW format.
 *
 * @constructor Creates a new instance of [StructuredToRawConverter]
 * @param conversionContext [ConversionContext]
 * @param structuredRmObject RM object in STRUCTURED format
 */
@Suppress("MoveLambdaOutsideParentheses")
class StructuredToRawConverter(conversionContext: ConversionContext, private val structuredRmObject: ObjectNode) {

    companion object {
        private val FIXED_WEB_TEMPLATE_INPUT_TYPES = listOf(
            WebTemplateInputType.BOOLEAN,
            WebTemplateInputType.CODED_TEXT,
            WebTemplateInputType.INTEGER,
            WebTemplateInputType.TEXT)
    }

    private val genericFieldFeederAudit = GenericFieldExtractor.invoke(structuredRmObject)
    private val conversionContext: ConversionContext = ConversionContextExtractor.invoke(structuredRmObject, conversionContext)


    /**
     * Holder for singleton objects that were created in the chain with multiple nodes (only possibilities are ITEM_STRUCTURE and HISTORY)
     * Note that it is cheaper to retrieve this object from the map then to create new instance of the object and set value with reflection.
     */
    private val firstChainSingletonHolder: MutableMap<Pair<AmNode, String>, RmObject> = mutableMapOf()


    /**
     * Converts the RM object in STRUCTURED format to the RM object in RAW format.
     *
     * @return RM object in RAW format
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : RmObject> convert(): T? {
        val rmObject = if (conversionContext.aqlPath.isNullOrBlank() && conversionContext.webTemplatePath.isNullOrBlank()) {
            val composition: Composition? = convertObjectNode(
                getObjectNodeForWebTemplateSegment(structuredRmObject, conversionContext.getWebTemplate().tree.jsonId) ?: return null,
                conversionContext.getWebTemplate().tree,
                WebTemplatePath(conversionContext.getWebTemplate().tree.jsonId)) as Composition?

            composition?.also {
                val archetype: Archetyped = it.archetypeDetails ?: Archetyped().also { archetype -> it.archetypeDetails = archetype }
                it.archetypeDetails = archetype.apply {
                    this.templateId = TemplateId().apply { this.value = conversionContext.getWebTemplate().templateId }
                }
            }
            composition as T?
        } else {
            val webTemplateNode =
                if (conversionContext.aqlPath.isNullOrBlank())
                    conversionContext.getWebTemplate().findWebTemplateNode(conversionContext.webTemplatePath!!)
                else
                    conversionContext.getWebTemplate().findWebTemplateNodeByAqlPath(conversionContext.aqlPath)

            convertObjectNode(
                getObjectNodeForWebTemplateSegment(structuredRmObject, webTemplateNode.jsonId) ?: (getObjectNodeForWebTemplatePath(structuredRmObject, webTemplateNode.id) ?: return null),
                webTemplateNode,
                WebTemplatePath(webTemplateNode.jsonId)) as T?
        }

        if (genericFieldFeederAudit != null && rmObject is Locatable && rmObject.feederAudit == null) {
            rmObject.feederAudit = genericFieldFeederAudit
        }

        return rmObject
    }

    /**
     * Recursively converts the RM object in STRUCTURED format to the RM object in RAW format.
     *
     * @param objectNode RM object in STRUCTURED format
     * @param webTemplateNode [WebTemplateNode]
     * @param webTemplatePath Web template path from root to the current [WebTemplateNode]
     * @return RM object in RAW format
     */
    @Suppress("UNCHECKED_CAST")
    private fun convertObjectNode(objectNode: ObjectNode, webTemplateNode: WebTemplateNode, webTemplatePath: WebTemplatePath): Any? {
        if (objectNode.isEmpty) { //ObjectNode is empty; nothing will be created.
            return null
        }

        val map = webTemplateNode.children.associateBy { it.jsonId }

        val chainConversionResult: MutableList<ChainConversionResult> = mutableListOf()
        val specialCaseHandlers: MutableList<(RmObject) -> Unit> = mutableListOf()

        objectNode.fields().forEach { (key, value) ->
            if ((value.isArray && value.isEmpty) || (value.isObject && value.isEmpty)) {  //JsonNode is empty; nothing will be created.
                return@forEach
            }

            val arrayNode: ArrayNode = if (value.isArray) value as ArrayNode else ConversionObjectMapper.createArrayNode().apply { this.add(value) } //Maybe value was not passed as array node

            if (!key.startsWith("transient_") && key != "ctx" && !key.startsWith("ctx/")) { //Skip transient fields and ctx values (ctx was already set to the ConversionContext).
                val childWebTemplateNode = map[key]
                when {
                    childWebTemplateNode != null -> {  //Handle WebTemplateNode structure.
                        val chain = getAmNodeChain(webTemplateNode.amNode, childWebTemplateNode.amNode)

                        convertArrayNode(
                            arrayNode,
                            chain,
                            webTemplatePath + key,
                            { node, wtPath, parents -> createChildNode(key, node, wtPath, parents, childWebTemplateNode) }).also {
                            chainConversionResult.add(it)
                        }
                    }
                    SpecialCaseRmObjectHandlerProvider.isSpecialCaseAttribute(key) -> { //Handle special cases (HISTORY origin and DvInterval booleans).
                        specialCaseHandlers.add { rmObject ->
                            SpecialCaseRmObjectHandlerProvider.provide(key)
                                .handle(conversionContext, webTemplateNode.amNode, arrayNode, rmObject, webTemplatePath)
                        }
                    }
                    else -> { // Handle RM attributes.
                        convertArrayNode(key, arrayNode, webTemplateNode.amNode, webTemplatePath + key).also {
                            chainConversionResult.add(it)
                        }
                    }
                }
            }
        }

        //Handle mandatory fixed values that were not present in the RM object but they need to be set!
        map.entries.forEach { (key, value) ->
            if (!objectNode.has(key) || objectNode[key].let { (it.isArray && it.isEmpty) || (it.isObject && it.isEmpty) }) {
                val mandatoryFixedInput = getMandatoryFields(value)
                if (mandatoryFixedInput != null) {
                    val chain = getAmNodeChain(webTemplateNode.amNode, value.amNode)
                    convertArrayNode(
                        ConversionObjectMapper.createArrayNode().apply { this.add(ConversionObjectMapper.nullNode()) },
                        chain,
                        webTemplatePath + key,
                        { node, wtPath, parents -> createChildNode(key, node, wtPath, parents, value) }).also {
                        chainConversionResult.add(it)
                    }
                }
            }
        }

        //ChainConversionResult is empty if the created object is null or if the created collection is empty.
        if (chainConversionResult.all { it.isEmpty() } && specialCaseHandlers.isEmpty()) {
            return null
        }

        //Post-process all collections that were populated in the chain (firstly created collections are excluded).
        chainConversionResult.forEach { it.postProcessors.map { postProcessor -> postProcessor.invoke() } }

        val createdRmObject = RmObjectNodeFactoryProvider.provide(webTemplateNode.rmType).create(conversionContext, webTemplateNode.amNode, webTemplatePath)

        val identityMap = IdentityHashMap<Any, () -> Unit>()
        chainConversionResult.mapNotNull { it.setterFunction }.forEach {
            val result = it.invoke(createdRmObject)
            identityMap[result.first] = result.second
        }

        specialCaseHandlers.forEach { it.invoke(createdRmObject) }
        identityMap.values.forEach { it.invoke() } //Post-process firstly created the collection in the chain.

        PostProcessDelegator.delegate(conversionContext, webTemplateNode.amNode, createdRmObject, webTemplatePath)
        return if (createdRmObject.isEmpty()) null else createdRmObject
    }

    /**
     * Converts the RM object or [Collection] of the RM objects in STRUCTURED format to RAW format for the RM attributes.
     *
     * @param key JSON entry key
     * @param arrayNode JSON entry value
     * @param amNode [AmNode]
     * @param webTemplatePath Web template path from root to the current [WebTemplateNode]
     * @return [Pair] of RM attribute [AmNode] and a created RM object or [Collection] of the RM objects in RAW format
     */
    private fun convertArrayNode(key: String, arrayNode: ArrayNode, amNode: AmNode, webTemplatePath: WebTemplatePath): ChainConversionResult {
        val amAttribute = getAmAttribute(key, amNode)

        if (amAttribute == null || amAttribute.children.size > 1) {
            if (arrayNode.isEmptyInDepth()) {
                return ChainConversionResult.nothing()
            } else {
                throw ConversionException("${amNode.rmType} has no attribute ${webTemplatePath.key}", webTemplatePath.toString())
            }
        }

        val amAttributeAmNode = amAttribute.children[0]
        return convertForSingletonChain( //RM attributes always have singleton chains.
            arrayNode,
            amAttributeAmNode,
            webTemplatePath,
            { node, wtPath, parents -> createChildNode(key, node, wtPath, amAttributeAmNode, parents) })
    }

    /**
     * Converts the RM object or [Collection] of the RM objects in STRUCTURED format to RAW format for the RM attributes.
     *
     * @param arrayNode JSON entry value
     * @param amNode [AmNode]
     * @param webTemplatePath Web template path from root to the current [WebTemplateNode]
     * @param factoryFunction Function for creating a RM object leaf node
     * @return [Pair] of RM attribute [AmNode] and a created RM object or [Collection] of the RM objects in RAW format
     */
    @Suppress("UNCHECKED_CAST")
    private fun convertForSingletonChain(
            arrayNode: ArrayNode,
            amNode: AmNode,
            webTemplatePath: WebTemplatePath,
            factoryFunction: (JsonNode, WebTemplatePath, List<Any>) -> Any?): ChainConversionResult =
        if (amNode.isCollectionOnParent()) {
            val collection = mutableListOf<Any>()
            arrayNode.forEachIndexed { index, jsonNode ->
                factoryFunction.invoke(jsonNode, webTemplatePath.copy(index = index), emptyList())?.also { createdValue -> collection.add(createdValue) }
            }
            if (collection.isEmpty()) {
                ChainConversionResult.nothing()
            } else {
                /*
                    We need to post-process collection later. For example, different CLUSTERS can be added to the CLUSTER.
                    This is why we always want to add objects to the collection, not set them.
                 */
                ChainConversionResult(
                    value = collection,
                    setterFunction = { parent ->
                        val exitingCollection = amNode.getOnParent(parent) as MutableCollection<Any>
                        exitingCollection.addAll(collection)
                        Pair(exitingCollection, { PostProcessDelegator.delegate(conversionContext, amNode, exitingCollection, webTemplatePath) })
                    })
            }
        } else {
            if (arrayNode.size() > 1) {
                throw ConversionException("JSON array with single value is expected", webTemplatePath.toString())
            } else {
                factoryFunction.invoke(arrayNode[0], webTemplatePath, emptyList())?.let {
                    ChainConversionResult( //Created object was already post-processed by the factory function.
                        value = it,
                        setterFunction = { parent ->
                            amNode.setOnParent(parent, it)
                            Pair(it, { })
                        })
                } ?: ChainConversionResult.nothing()
            }
        }

    /**
     * Recursively converts the RM object or [Collection] or RM objects in STRUCTURED format to RAW format.
     *
     * Note that some nodes are not presented in [WebTemplateNode] (ITEM_STRUCTURE for example). This function creates those elements as well.
     *
     * @param arrayNode RM object in STRUCTURED format
     * @param chain [List] of [AmNode] from parent [WebTemplateNode] [AmNode] to child [WebTemplateNode] [AmNode]
     * @param webTemplatePath Web template path from the root to the current [WebTemplateNode]
     * @param factoryFunction Function for creating a RM object leaf node
     * @return RM object OR [List] of RM objects in RAW format
     */
    @Suppress("UNCHECKED_CAST")
    private fun convertArrayNode(
            arrayNode: ArrayNode,
            chain: List<AmNode>,
            webTemplatePath: WebTemplatePath,
            factoryFunction: (JsonNode, WebTemplatePath, List<Any>) -> Any?): ChainConversionResult {
        val firstAmNode = chain.first()
        if (chain.size == 1)
            return convertForSingletonChain(arrayNode, firstAmNode, webTemplatePath, factoryFunction)
        else {
            if (firstAmNode.isCollectionOnParent()) {
                //Only possible case when first ELEMENT in chain requires collection is when we want to add ELEMENTS to the CLUSTER.
                if (firstAmNode.isForElement()) {
                    val collection = mutableListOf<Any>()
                    val chainPostProcessors = mutableListOf<() -> Unit>()

                    arrayNode.forEachIndexed { index, jsonNode ->
                        val createdValue: Element = RmObjectNodeFactoryProvider.provide(firstAmNode.rmType).create(conversionContext, firstAmNode, webTemplatePath) as Element
                        collection.add(createdValue) //We want to add the created ELEMENT to the collection to ensure the order of the elements. Maybe we will need to add some more DV_TEXT or DV_CODED_TEXT (for "|other" attribute).
                        val postProcessors = mutableListOf<() -> Unit>()
                        convertInChain(
                            mutableListOf(collection, createdValue),
                            jsonNode,
                            chain.drop(1),
                            webTemplatePath.copy(index = index),
                            postProcessors,
                            factoryFunction)
                        chainPostProcessors.addAll(postProcessors)
                        if (createdValue.isEmpty()) { //If ELEMENT is empty, remove it from the list, otherwise post-process it.
                            collection.remove(createdValue)
                        } else {
                            PostProcessDelegator.delegate(conversionContext, firstAmNode, createdValue, webTemplatePath.copy(index = index))
                        }
                    }

                    return if (collection.isEmpty()) {
                        ChainConversionResult.nothing()
                    } else {
                        /*
                            We need to post-process the collection later. For example, DATA VALUES with different web template paths need to be added to the CLUSTER.
                            This is why we always want to add objects to the collection, not set them.
                        */
                        ChainConversionResult(
                            value = collection,
                            setterFunction = { parent ->
                                val exitingCollection = firstAmNode.getOnParent(parent) as MutableCollection<Any>
                                exitingCollection.addAll(collection)
                                Pair(exitingCollection, { PostProcessDelegator.delegate(conversionContext, firstAmNode, exitingCollection, webTemplatePath) })
                            })
                    }
                } else {
                    throw ConversionException(
                        "Unsupported operation: trying to add ${firstAmNode.rmType} to the ${firstAmNode.parent?.rmType}",
                        webTemplatePath.toString())
                }
            } else {
                /*
                    Retrieve or create a new object (ITEM_STRUCTURE or HISTORY).
                    If the object was already created beforehand, we just add ITEMS or EVENTS to the collection in the chain.
                */
                val existingValue = firstChainSingletonHolder[Pair(firstAmNode, webTemplatePath.parent?.toString() ?: "")]
                val value = existingValue ?: RmObjectNodeFactoryProvider.provide(firstAmNode.rmType).create(conversionContext, firstAmNode, webTemplatePath)

                val chainPostProcessors = mutableListOf<() -> Unit>()
                convertInChain(mutableListOf(value), arrayNode, chain.drop(1), webTemplatePath, chainPostProcessors, factoryFunction)

                return if (existingValue == null) {
                    if (value.isEmpty()) { //If RM object is empty, do nothing. If we will need it, we will create it again.
                        ChainConversionResult.nothing()
                    } else {
                        firstChainSingletonHolder[Pair(firstAmNode, webTemplatePath.parent?.toString() ?: "")] = value
                        ChainConversionResult(
                            value = value,
                            postProcessors = chainPostProcessors,
                            setterFunction = { parent ->
                                firstAmNode.setOnParent(parent, value)
                                Pair(value, { PostProcessDelegator.delegate(conversionContext, firstAmNode, value, webTemplatePath) })  //We want to post-process this element only when it was created.
                            })
                    }
                } else {
                    //Maybe one of the collection in the chain was empty and we populated it. In this case, just return the post-processors.
                    ChainConversionResult.nothing()
                }
            }
        }
    }

    /**
     * Recursively sets child on the parent RM object in RAW format.
     * Node that the deepest child is created from a RM object in STRUCTURED format.
     *
     * @param parents Parents created in [AmNode] chain before leaf node
     * @param jsonNode RM object in STRUCTURED format
     * @param chain [List] of [AmNode] from parent [WebTemplateNode] [AmNode] to child [WebTemplateNode] [AmNode]
     * @param webTemplatePath Web template path from root to current [WebTemplateNode]
     * @param factoryFunction Function for creating a RM object in RAW format
     */
    @Suppress("UNCHECKED_CAST")
    private fun convertInChain(
            parents: MutableList<Any>,
            jsonNode: JsonNode,
            chain: List<AmNode>,
            webTemplatePath: WebTemplatePath,
            chainPostProcessors: MutableList<() -> Unit>,
            factoryFunction: (JsonNode, WebTemplatePath, List<Any>) -> Any?) {
        val amNode: AmNode = chain.first()
        val directParent: Any = parents.last()

        if (chain.size == 1) { //All omitted RM objects in the chain were created. Create leaf object or collection of objects and set it or add it to the parent.
            val createdValue = factoryFunction.invoke(jsonNode, webTemplatePath, parents) ?: return
            if (amNode.isCollectionOnParent()) {
                val collection = amNode.getOnParent(directParent) as MutableCollection<Any>
                val isEmpty = collection.isEmpty()
                if (createdValue is Collection<*>)
                    collection.addAll(createdValue as MutableCollection<Any>)
                else
                    collection.add(createdValue)

                /*
                    We need to post-process the collection later only once. For example, CLUSTER with different web template paths need to be added to the CLUSTER.
                    Because we add objects to the collection that was retrieved from the parent, collection will be filled with all the objects when it will be post-processed.
                 */
                if (isEmpty && collection.isNotEmpty()) {
                    chainPostProcessors.add({ PostProcessDelegator.delegate(conversionContext, amNode, collection, webTemplatePath) })
                }
            } else {
                /*
                    We have an array of arrays in STRUCTURED format.
                    Maybe we created the collection with only one object.
                    In that case, get the first object from the collection and set it to the parent.
                 */
                if (createdValue is Collection<*> && createdValue.size == 1)
                    amNode.setOnParent(directParent, (createdValue as Collection<Any>).iterator().next())
                else
                    amNode.setOnParent(directParent, createdValue)
            }
            return
        }

        if (amNode.isCollectionOnParent()) {
            val collection = amNode.getOnParent(directParent) as MutableCollection<Any>
            val isEmpty = collection.isEmpty()
            if (amNode.rmType == "ELEMENT") { //Objects are added to the collection in the chain only if AmNode is for ELEMENT RM type.
                jsonNode.forEachIndexed { index, node ->
                    val createdValue = RmObjectNodeFactoryProvider.provide(amNode.rmType).create(conversionContext, amNode, webTemplatePath)
                    collection.add(createdValue) //We want to add the created ELEMENT to the collection to ensure the order of the elements. Maybe we will need to add some more DV_TEXT or DV_CODED_TEXT (for "|other" attribute).
                    convertInChain(
                        mutableListOf<Any>().also { it.addAll(parents); it.add(createdValue) },
                        node,
                        chain.drop(1),
                        webTemplatePath.copy(index = index),
                        chainPostProcessors,
                        factoryFunction)
                    if (createdValue.isEmpty()) {
                        collection.remove(createdValue)
                    } else {
                        PostProcessDelegator.delegate(conversionContext, amNode, createdValue, webTemplatePath.copy(index = index))
                    }
                }
            } else { //Otherwise, we will retrieve the first RM object in the collection and recursively set objects or add them in the chain.
                val firstCollectionRmObject =
                    if (isEmpty)
                        RmObjectNodeFactoryProvider.provide(amNode.rmType).create(conversionContext, amNode, webTemplatePath)
                    else
                        collection.iterator().next() as RmObject

                convertInChain(
                    parents.also { it.add(firstCollectionRmObject) },
                    jsonNode,
                    chain.drop(1),
                    webTemplatePath,
                    chainPostProcessors,
                    factoryFunction)

                if (isEmpty) {  //We want to post-process this object only when it was created.
                    PostProcessDelegator.delegate(conversionContext, amNode, firstCollectionRmObject, webTemplatePath)
                    if (firstCollectionRmObject.isNotEmpty()) {  //If RM object is empty, do nothing. If we will need it, we will create it again.
                        collection.add(firstCollectionRmObject)
                    }
                }
            }

            /*
                We need to post-process the collection later only once. For example, CLUSTER with different web template paths need to be added to the CLUSTER.
                Because we add objects to the collection that was retrieved from the parent, the collection will be filled with all the objects when it will be post-processed.
            */
            if (isEmpty && collection.isNotEmpty()) {
                chainPostProcessors.add({ PostProcessDelegator.delegate(conversionContext, amNode, collection, webTemplatePath) })
            }
        } else {
            val retrievedValue = amNode.getOnParent(directParent) as RmObject?
            val value: RmObject = retrievedValue ?: RmObjectNodeFactoryProvider.provide(amNode.rmType).create(conversionContext, amNode, webTemplatePath)

            convertInChain(parents.also { it.add(value) }, jsonNode, chain.drop(1), webTemplatePath, chainPostProcessors, factoryFunction)
            if (retrievedValue == null) { //We want to post-process this element only when it was created.
                PostProcessDelegator.delegate(conversionContext, amNode, value, webTemplatePath)
                if (value.isNotEmpty()) { //If RM object is empty, do nothing. If we will need it, we will create it again.
                    amNode.setOnParent(directParent, value)
                }
            }
        }
    }

    /**
     * Recursively converts the RM object in STRUCTURED format to the RM object or to [Collection] of the RM objects in RAW format.
     *
     * @param key JSON entry key
     * @param value JSON entry value
     * @param webTemplatePath Web template path from the root to the current [WebTemplateNode]
     * @param parents Parents created in [AmNode] chain before leaf node
     * @param webTemplateNode [WebTemplateNode]
     *
     * @return RM object or [Collection] of RM objects in RAW format
     */
    private fun createChildNode(
            key: String,
            value: JsonNode,
            webTemplatePath: WebTemplatePath,
            parents: List<Any>,
            webTemplateNode: WebTemplateNode): Any? =
        if (value.isArray) {
            val collection = mutableListOf<Any>()
            value.forEachIndexed { index, node ->
                createChildNode(key, node, webTemplatePath.copy(webTemplateNode.amNode, index), parents, webTemplateNode)?.also {
                    collection.add(it)
                }
            }
            collection
        } else {
            when {
                value.isObject && (value.has("|raw") || value.has("raw")) -> { //DO NOT POST-PROCESS RAW values
                    ConversionObjectMapper.convertRawJsonNode(
                        conversionContext,
                        webTemplateNode.amNode,
                        value,
                        webTemplatePath.copy(webTemplateNode.amNode))
                }
                webTemplateNode.children.isEmpty() -> {
                    if (RmUtils.isRmClass(webTemplateNode.amNode.getTypeOnParent().type)) {
                        RmObjectLeafNodeFactoryProvider
                            .getFactory(webTemplateNode.rmType)
                            .create(
                                conversionContext,
                                webTemplateNode.amNode,
                                value,
                                webTemplatePath.copy(webTemplateNode.amNode),
                                parents,
                                getMandatoryFields(webTemplateNode))
                    } else {
                        ConversionObjectMapper.convertValue(value, webTemplateNode.amNode.getTypeOnParent().type)
                    }
                }
                value.isNull || value.isMissingNode -> null
                else -> convertObjectNode(value as ObjectNode, webTemplateNode, webTemplatePath.copy(webTemplateNode.amNode))
            }
        }

    /**
     * Converts the RM object in STRUCTURED format to the RM object or to [Collection] of the RM objects in RAW format for the RM attribute.
     *
     * @param key JSON entry key
     * @param value JSON entry value
     * @param webTemplatePath Web template path from the root to the current [WebTemplateNode]
     * @param amNode [AmNode]
     * @param parents Parents created in [AmNode] chain before leaf node
     *
     * @return RM object or [Collection] of RM objects in RAW format
     */
    private fun createChildNode(key: String, value: JsonNode, webTemplatePath: WebTemplatePath, amNode: AmNode, parents: List<Any>): Any? =
        if (value.isArray) {
            val collection = mutableListOf<Any>()
            value.forEachIndexed { index, node ->
                createChildNode(key, node, webTemplatePath.copy(amNode, index), amNode, parents)?.also {
                    collection.add(it)
                }
            }
            collection
        } else {
            when {
                value.isObject && (value.has("|raw") || value.has("raw")) -> {
                    ConversionObjectMapper.convertRawJsonNode(conversionContext, amNode, value, webTemplatePath.copy(amNode))
                }
                RmUtils.isRmClass(amNode.getTypeOnParent().type) -> {
                    RmObjectLeafNodeFactoryProvider.getFactory(amNode.rmType).create(conversionContext, amNode, value, webTemplatePath.copy(amNode), parents)
                }
                else -> ConversionObjectMapper.convertValue(value, amNode.getTypeOnParent().type)
            }
        }

    /**
     * Creates [List] of [AmNode] from parent [AmNode] to child [AmNode].
     * Note that parent [AmNode] is excluded from the list.
     *
     * @param parent Parent [AmNode]
     * @param child Child [AmNode]
     * @return n [List] of [AmNode] from the parent [AmNode] to the child [AmNode]
     */
    private fun getAmNodeChain(parent: AmNode?, child: AmNode): MutableList<AmNode> =
        when {
            parent == null -> mutableListOf(child)
            child == parent -> mutableListOf()
            else -> getAmNodeChain(parent, child.parent!!).apply { this.add(child) }
        }

    /**
     * Retrieves [AmAttribute] from [AmNode].
     *
     * @param key JSON entry key
     * @param amNode [AmNode] from which [AmAttribute] are retrieved
     * @return [AmAttribute] if found, otherwise, return null
     */
    private fun getAmAttribute(key: String, amNode: AmNode): AmAttribute? =
        amNode.attributes[key] ?: if (key.startsWith("_"))
            with(key.substring(1)) {
                amNode.attributes[this] ?: amNode.attributes["${this}s"]
            }
        else
            null

    /**
     * Retrieves [WebTemplateInput] from [WebTemplateNode] for mandatory [DataValue] fields.
     *
     * @param webTemplateNode [WebTemplateNode]
     * @return [WebTemplateInput] if found, otherwise, return null
     */
    private fun getMandatoryFields(webTemplateNode: WebTemplateNode): WebTemplateInput? =
        with(webTemplateNode.children.isEmpty() && webTemplateNode.input != null && webTemplateNode.occurences.min != null && webTemplateNode.occurences.min == 1) {
            when {
                this && webTemplateNode.input.isFixed && FIXED_WEB_TEMPLATE_INPUT_TYPES.contains(webTemplateNode.input.type) -> webTemplateNode.input
                this && !webTemplateNode.input.isFixed && WebTemplateInputType.BOOLEAN == webTemplateNode.input.type -> webTemplateNode.input
                else -> null
            }
        }


    /**
     * Data class that holds instructions and objects created during the single chain conversion.
     *
     * @constructor Creates a new instance of [ChainConversionResult]
     * @param value Object or [Collection] of objects that were created during during the single chain conversion.
     * @param postProcessors [List] or post-processor calls supplier that needs to be post-processed later (only collections for omitted objects)
     * @param setterFunction Function that sets created object or [Collection] of objects to the parent and returns [Pair] of produced object or [Collection] of objects and post-processor supplier (only if collection was created)
     */
    data class ChainConversionResult(
            val value: Any? = null,
            val postProcessors: List<() -> Unit> = listOf(),
            val setterFunction: ((Any) -> Pair<Any, () -> Unit>)? = null) {
        /**
         * Checks if this [ChainConversionResult] produces no objects.
         *
         * @return [Boolean] indicating if this [ChainConversionResult] produces no objects or not
         */
        fun isEmpty(): Boolean = value == null || (value is Collection<*> && value.isEmpty())

        /**
         * Checks if this [ChainConversionResult] produces any objects.
         *
         * @return [Boolean] indicating if this [ChainConversionResult] produces any objects or not
         */
        fun isNotEmpty(): Boolean = !isEmpty()

        companion object {
            private val NOTHING = ChainConversionResult()

            /**
             * Returns a singleton instance of [ChainConversionResult] that has no instructions.
             *
             * @return Singleton instance of [ChainConversionResult] that has no instructions
             */
            @JvmStatic
            fun nothing() = NOTHING
        }
    }
}
