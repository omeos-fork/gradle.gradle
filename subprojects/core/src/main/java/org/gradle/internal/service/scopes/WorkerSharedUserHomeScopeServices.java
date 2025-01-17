/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.service.scopes;

import org.gradle.cache.GlobalCache;
import org.gradle.cache.UnscopedCacheBuilderFactory;
import org.gradle.cache.internal.CacheFactory;
import org.gradle.cache.internal.DefaultUnscopedCacheBuilderFactory;
import org.gradle.cache.internal.scopes.DefaultGlobalScopedCacheBuilderFactory;
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory;
import org.gradle.initialization.layout.GlobalCacheDir;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistrationProvider;

public class WorkerSharedUserHomeScopeServices implements ServiceRegistrationProvider {
    public void configure(ServiceRegistration registration) {
        registration.add(GlobalCacheDir.class);
    }

    @Provides
    UnscopedCacheBuilderFactory createCacheRepository(CacheFactory cacheFactory) {
        return new DefaultUnscopedCacheBuilderFactory(cacheFactory);
    }

    @Provides({GlobalScopedCacheBuilderFactory.class, GlobalCache.class})
    DefaultGlobalScopedCacheBuilderFactory createGlobalScopedCache(GlobalCacheDir globalCacheDir, UnscopedCacheBuilderFactory unscopedCacheBuilderFactory) {
        return new DefaultGlobalScopedCacheBuilderFactory(globalCacheDir.getDir(), unscopedCacheBuilderFactory);
    }
}
