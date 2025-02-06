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

import org.gradle.api.Describable
import org.gradle.api.attributes.Attribute
import org.gradle.util.AttributeTestUtil

/**
 * Unit tests for the {@link FreezableAttributeContainer} class.
 */
final class FreezableAttributeContainerTest extends AbstractAttributeContainerTest {
    @Override
    protected <T> FreezableAttributeContainer getContainer(Map<Attribute<T>, T> attributes = [:]) {
        def attributesFactory = AttributeTestUtil.attributesFactory()
        def mutableContainer = new DefaultMutableAttributeContainer(attributesFactory, AttributeTestUtil.attributeValueIsolator())
        FreezableAttributeContainer container = new FreezableAttributeContainer(mutableContainer, { "owner" } as Describable);
        attributes.forEach { key, value ->
            container.attribute(key, value)
        }
        return container
    }
}
