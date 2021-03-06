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

package care.better.platform.web.template.converter

import care.better.platform.utils.JSR310ConversionUtils
import care.better.platform.web.template.WebTemplate
import care.better.platform.web.template.abstraction.AbstractWebTemplateTest
import care.better.platform.web.template.converter.raw.context.ConversionContext
import com.google.common.collect.ImmutableMap
import com.marand.thinkehr.web.WebTemplateBuilderContext
import com.marand.thinkehr.web.build.WTBuilder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.openehr.rm.composition.Composition
import org.openehr.rm.composition.Observation
import org.openehr.rm.composition.Section
import java.io.IOException
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.xml.bind.JAXBException

/**
 * @author Primoz Delopst
 * @since 3.1.0
 */
class LocationTest : AbstractWebTemplateTest() {
    @Test
    @Throws(IOException::class, JAXBException::class)
    fun testLocation() {
        val builderContext = WebTemplateBuilderContext("sl")
        val webTemplate: WebTemplate = WTBuilder.build(getTemplate("/convert/templates/Demo Vitals.opt"), builderContext)
        val dateTime = ZonedDateTime.of(2015, 1, 1, 10, 31, 16, 0, ZoneId.systemDefault()).toOffsetDateTime()
        val composition: Composition? = webTemplate.convertFromFlatToRaw(
            ImmutableMap.builder<String, String>()
                .put("ctx/language", "sl")
                .put("ctx/territory", "SI")
                .put("ctx/composer_name", "Composer")
                .put("ctx/id_scheme", "ispek")
                .put("ctx/id_namespace", "ispek")
                .put("ctx/end_time", "2016-01-01T12:30:30Z")
                .put("ctx/location", "1234 Best Exotic Marigold Hotel")
                .put("vitals/vitals/haemoglobin_a1c/history_origin", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(dateTime))
                .put("vitals/vitals/haemoglobin_a1c/any_event/test_status|terminology", "local")
                .put("vitals/vitals/haemoglobin_a1c/any_event/test_status|code", "at0037")
                .build(),
            ConversionContext.create().build())

        val section = composition!!.content[0] as Section
        val observation = section.items[0] as Observation
        assertThat(JSR310ConversionUtils.toOffsetDateTime(observation.data!!.origin!!).compareTo(dateTime)).isEqualTo(0)
        assertThat(composition.context!!.endTime!!.value).isEqualTo("2016-01-01T12:30:30Z")
        assertThat(composition.context!!.location).isEqualTo("1234 Best Exotic Marigold Hotel")
    }
}
