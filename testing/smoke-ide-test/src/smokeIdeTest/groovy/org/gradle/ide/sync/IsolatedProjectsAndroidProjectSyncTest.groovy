/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.ide.sync

import org.gradle.ide.sync.fixtures.IsolatedProjectsIdeSyncFixture
import org.gradle.integtests.fixtures.versions.AndroidGradlePluginVersions
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

class IsolatedProjectsAndroidProjectSyncTest extends AbstractIdeSyncTest {

    // https://developer.android.com/build/releases/gradle-plugin
    private final static String AGP_VERSION = new AndroidGradlePluginVersions().getLatest()

    private IsolatedProjectsIdeSyncFixture fixture = new IsolatedProjectsIdeSyncFixture(testDirectory)

    @Requires(
        value = UnitTestPreconditions.MacOs,
        reason = "intellij-ide-starter can't download AndroidStudio distribution for Linux yet."
    )
    def "can sync simple Android build without problems"() {
        given:
        simpleAndroidProject(AGP_VERSION)

        when:
        androidStudioSync(ANDROID_STUDIO_VERSION)

        then:
        fixture.assertHtmlReportHasNoProblems()
    }

    private void simpleAndroidProject(String agpVersion) {
        // A default configuration for a project generated by Android Studio
        file("settings.gradle") << """
            pluginManagement {
                repositories {
                    google {
                        content {
                            includeGroupByRegex("com.android.*")
                            includeGroupByRegex("com.google.*")
                            includeGroupByRegex("androidx.*")
                        }
                    }
                    mavenCentral()
                    gradlePluginPortal()
                }
            }

            dependencyResolutionManagement {
                repositories {
                    google()
                    mavenCentral()
                }
            }

            rootProject.name = 'project-under-test'
            include ':app'
            include ':lib'
        """

        file("build.gradle") << """
            plugins {
                id 'com.android.application' version '$agpVersion' apply false
                id 'com.android.library' version '$agpVersion' apply false
            }
        """

        file("gradle.properties") << """
            org.gradle.configuration-cache.problems=warn
            org.gradle.unsafe.isolated-projects=true
        """

        file("app/build.gradle") << """
            plugins {
                id 'com.android.application'
            }

            android {
                namespace = "com.example.application"
                compileSdk = 34
                defaultConfig {
                    applicationId = "com.example.application"
                    minSdk = 24
                    targetSdk = 34
                    versionCode = 1
                    versionName = "1.0"
                }
            }

            dependencies {
                implementation(project(':lib'))
            }
        """

        file("lib/build.gradle") << """
            plugins {
                id 'com.android.library'
            }

            android {
                namespace = "com.example.lib"
                compileSdk = 34
                defaultConfig {
                    minSdk = 24
                    targetSdk = 34
                }
            }
        """
    }
}
