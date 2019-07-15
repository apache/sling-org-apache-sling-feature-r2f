/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.sling.feature.r2f.impl;

import static org.osgi.framework.wiring.BundleWiring.LISTRESOURCES_RECURSE;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Map;
import java.util.Properties;
import java.util.Map.Entry;
import java.util.function.Function;

import org.apache.sling.feature.Artifact;
import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Feature;
import org.osgi.framework.Bundle;
import org.osgi.framework.Version;
import org.osgi.framework.startlevel.BundleStartLevel;
import org.osgi.framework.wiring.BundleWiring;

final class Bundle2ArtifactMapper extends AbstractFeatureElementConsumer<Artifact> implements Function<Bundle, Artifact> {

    private static final String MAVEN_METADATA_PATH = "/META-INF/maven";

    private static final String POM_PROPERTIES_RESOURCE_NAME = "pom.properties";

    private static final String GROUP_ID = "groupId";

    private static final String ARTIFACT_ID = "artifactId";

    private static final String VERSION = "version";

    private static final String CLASSIFIER = "classifier";

    private final Map<Entry<String, Version>, ArtifactId> bvm;

    public Bundle2ArtifactMapper(Feature targetFeature, Map<Entry<String, Version>, ArtifactId> bvm) {
        super(targetFeature);
        this.bvm = bvm;
    }

    @Override
    public Artifact apply(Bundle bundle) {
        if (0 == bundle.getBundleId()) {
            // ignore the OSGi framework
            return null;
        }

        ArtifactId id = bvm.get(new AbstractMap.SimpleEntry<>(bundle.getSymbolicName(), bundle.getVersion()));
        if (id == null) {
            id = readGavFromPomProperties(bundle);
        }

        if (id == null) {
            // it must be a corner case exception...
            throw new IllegalStateException("Bundle "
                                            + bundle.getSymbolicName()
                                            + '-'
                                            + bundle.getVersion()
                                            + " not found in the 'idbsnver.properties' map nor it contains the META-INF/[groupId]/[artifactId]/pom.properties file");
        }

        Artifact artifact = new Artifact(id);

        BundleStartLevel bundleStartLevel = bundle.adapt(BundleStartLevel.class);
        int startOrder = bundleStartLevel.getStartLevel();
        artifact.setStartOrder(startOrder);

        return artifact;
    }

    private static ArtifactId readGavFromPomProperties(Bundle bundle) {
        Properties pomProperties = new Properties();

        BundleWiring bundleWiring = bundle.adapt(BundleWiring.class);
        Collection<String> pomPropertiesResources = bundleWiring.listResources(MAVEN_METADATA_PATH, POM_PROPERTIES_RESOURCE_NAME, LISTRESOURCES_RECURSE);

        if (pomPropertiesResources == null || pomPropertiesResources.isEmpty()) {
            return null;
        }

        URL pomPropertiesURL = getPomPropertiesURL(pomPropertiesResources, bundle);
        if (pomPropertiesURL == null) {
            return null;
        }

        try {
            URLConnection connection = pomPropertiesURL.openConnection();
            connection.connect();

            try (InputStream inStream = connection.getInputStream()) {
                pomProperties.load(inStream);
            }

            if (connection instanceof HttpURLConnection) {
                ((HttpURLConnection) connection).disconnect();
            }
        } catch (Throwable t) {
            throw new RuntimeException("An error occurred while reading "
                                       + pomPropertiesURL
                                       + " properties file from Bundle "
                                       + bundle.getSymbolicName(), t);
        }

        if (pomProperties.isEmpty()) {
            throw new RuntimeException("Bundle "
                                      + bundle.getSymbolicName()
                                      + " does not export valid Maven metadata");
        }

        String groupId = pomProperties.getProperty(GROUP_ID);
        String artifactId = pomProperties.getProperty(ARTIFACT_ID);
        String version = pomProperties.getProperty(VERSION);
        String classifier = pomProperties.getProperty(CLASSIFIER);

        return new ArtifactId(groupId, artifactId, version, classifier, null);
    }

    private static URL getPomPropertiesURL(Collection<String> pomPropertiesResources, Bundle bundle) {
        for (String pomPropertiesResource : pomPropertiesResources) {
            URL pomPropertiesURL = bundle.getEntry(pomPropertiesResource);

            if (pomPropertiesURL != null) {
                return pomPropertiesURL;
            }
        }

        return null;
    }

    @Override
    public void accept(Artifact artifact) {
        if (artifact != null) {
            getTargetFeature().getBundles().add(artifact);
        }
    }

}
