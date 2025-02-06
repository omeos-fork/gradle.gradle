/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.attributes

import org.gradle.api.Named
import org.gradle.api.attributes.Attribute
import org.gradle.util.AttributeTestUtil

/**
 * Unit tests for the {@link DefaultImmutableAttributesContainer} class.
 */
final class DefaultImmutableAttributeContainerTest extends AbstractAttributeContainerTest {
    @SuppressWarnings(['GroovyAssignabilityCheck', 'GrReassignedInClosureLocalVar'])
    @Override
    protected <T> DefaultImmutableAttributesContainer getContainer(Map<Attribute<T>, T> attributes = [:]) {
        DefaultImmutableAttributesContainer container = new DefaultImmutableAttributesContainer()
        attributes.forEach { key, value ->
            container = AttributeTestUtil.attributesFactory().concat(container, key, value)
        }
        return container
    }

    // This lenient coercing behavior is only available in the immutable container.  The mutable containers shouldn't ever need it
    def "if there is a string in the container, and you ask for it as a Named, you get back the same value do to coercion"() {
        given:
        def container = getContainer([(Attribute.of("test", String)): "value"])

        when:
        //noinspection GroovyAssignabilityCheck
        def result = (String) container.getAttribute(Attribute.of("test", Named))

        then:
        result == "value"
    }
}
