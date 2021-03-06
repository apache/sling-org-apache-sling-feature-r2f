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

import static java.nio.file.Files.newBufferedReader;
import static org.apache.sling.feature.builder.FeatureBuilder.assemble;
import static org.apache.sling.feature.diff.FeatureDiff.compareFeatures;
import static org.apache.sling.feature.io.json.FeatureJSONReader.read;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.stream.Stream;

import org.apache.sling.feature.ArtifactId;
import org.apache.sling.feature.Feature;
import org.apache.sling.feature.Prototype;
import org.apache.sling.feature.builder.BuilderContext;
import org.apache.sling.feature.builder.FeatureProvider;
import org.apache.sling.feature.diff.DiffRequest;
import org.apache.sling.feature.r2f.RuntimeEnvironment2FeatureModel;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Version;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

@Component(service = RuntimeEnvironment2FeatureModel.class)
public class RuntimeEnvironment2FeatureModelService implements RuntimeEnvironment2FeatureModel, FeatureProvider {

    // borrowed from org.apache.sling.feature.apiregions.impl.RegionEnforcer
    private static final String IDBSNVER_FILENAME = "idbsnver.properties";

    // borrowed from org.apache.sling.feature.apiregions.impl.RegionEnforcer
    private static final String PROPERTIES_RESOURCE_PREFIX = "sling.feature.apiregions.resource.";

    // borrowed from org.apache.sling.feature.apiregions.impl.RegionEnforcer
    private static final String PROPERTIES_FILE_LOCATION = "sling.feature.apiregions.location";

    // borrowed from org.apache.sling.feature.apiregions.impl.RegionEnforcer
    private static final String CLASSLOADER_PSEUDO_PROTOCOL = "classloader://";

    private static final String SLING_FEATURE_PROPERTY_NAME = "sling.feature";

    private static final String RUNNING_CLASSIFIER = "running";

    private static final String RUNTIME_CLASSIFIER = "runtime";

    private static final String PACKAGING_FEATURE = "slingosgifeature";

    private final Map<Entry<String, Version>, ArtifactId> bvm = new HashMap<>();

    protected BundleContext bundleContext;

    @Reference
    protected ConfigurationAdmin configurationAdmin;

    private Feature launchFeature;

    @Activate
    public void start(BundleContext bundleContext) {
        this.bundleContext = bundleContext;

        readBSNVerMap();
        readLaunchFeature();
    }

    private void readBSNVerMap() {
        try {
            URI idbsnverFile = getDataFileURI(bundleContext, IDBSNVER_FILENAME);

            Properties properties = new Properties();
            try (InputStream is = idbsnverFile.toURL().openStream()) {
                properties.load(is);
            }

            for (String artifactId : properties.stringPropertyNames()) {
                String[] bsnver = properties.getProperty(artifactId).split("~");
                String bundleSymbolicName = bsnver[0];
                String bundleVersion = bsnver[1];
                Version version = Version.valueOf(bundleVersion);

                bvm.put(new AbstractMap.SimpleEntry<>(bundleSymbolicName, version), ArtifactId.parse(artifactId));
            }
        } catch (Exception e) {
            throw new IllegalStateException("An error occurred while loading 'idbsnver.properties' file, impossible to assemble the bundles map", e);
        }
    }

    // borrowed from org.apache.sling.feature.apiregions.impl.RegionEnforcer
    private static URI getDataFileURI(BundleContext ctx, String name) throws Exception {
        String fn = ctx.getProperty(PROPERTIES_RESOURCE_PREFIX + name);
        if (fn == null) {
            String loc = ctx.getProperty(PROPERTIES_FILE_LOCATION);
            if (loc != null) {
                fn = loc + "/" + name;
            }
        }

        if (fn == null)
            throw new IOException("API Region Enforcement enabled, but no configuration found to find "
                                  + "region definition resource: "
                                  + name);

        if (fn.contains(":")) {
            if (fn.startsWith(CLASSLOADER_PSEUDO_PROTOCOL)) {
                // It's using the 'classloader:' protocol looks up the location from the
                // classloader
                String loc = fn.substring(CLASSLOADER_PSEUDO_PROTOCOL.length());
                if (!loc.startsWith("/"))
                    loc = "/" + loc;
                fn = RuntimeEnvironment2FeatureModelService.class.getResource(loc).toString();
            }
            // It's already a URL
            return new URI(fn);
        } else {
            // It's a file location
            return new File(fn).toURI();
        }
    }

    private void readLaunchFeature() {
        String launchFeatureLocation = bundleContext.getProperty(SLING_FEATURE_PROPERTY_NAME);

        if (launchFeatureLocation == null) {
            throw new IllegalStateException("Framework property 'sling.feature' is not set, impossible to assemble the launch Feature");
        }

        URI launchFeatureURI = URI.create(launchFeatureLocation);
        Path launchFeaturePath = Paths.get(launchFeatureURI);

        try (BufferedReader reader = newBufferedReader(launchFeaturePath)) {
            launchFeature = read(reader, launchFeatureLocation);
        } catch (IOException cause) {
            throw new UncheckedIOException(cause);
        }
    }

    @Deactivate
    public void stop() {
        bundleContext = null;
        bvm.clear();
        launchFeature = null;
    }

    @Override
    public Feature getLaunchFeature() {
        return launchFeature;
    }

    @Override
    public Feature getRunningFeature() {
        Feature targetFeature = new Feature(newId(RUNNING_CLASSIFIER));

        // collect all bundles

        Bundle[] bundles = bundleContext.getBundles();
        if (bundles != null) {
            Bundle2ArtifactMapper mapper = new Bundle2ArtifactMapper(targetFeature, bvm);

            Stream.of(bundles).map(mapper).filter(bundle -> bundle != null).forEach(mapper);
        }

        // collect all configurations

        try {
            Configuration[] configurations = configurationAdmin.listConfigurations(null);
            if (configurations != null && configurations.length > 0) {
                OSGiConfiguration2FeatureConfigurationMapper mapper = new OSGiConfiguration2FeatureConfigurationMapper(targetFeature);

                Stream.of(configurations).map(mapper).forEach(mapper);
            }
        } catch (Exception e) {
            // that should not happen
            throw new RuntimeException("Something went wrong while iterating over all available Configurations", e);
        }

        return targetFeature;
    }

    @Override
    public Feature getLaunch2RunningUpgradingFeature() {
        Feature runningFeature = getRunningFeature();

        // framework-properties can not be scanned in the BundleContext ATM
        // extensions can not be computed at runtime
        return compareFeatures(new DiffRequest()
                               .setPrevious(launchFeature)
                               .setCurrent(runningFeature)
                               .addIncludeComparator("bundles")
                               .addIncludeComparator("configurations"));
    }

    @Override
    public Feature getRuntimeFeature() {
        Feature launch2RunningUpgradingFeature = getLaunch2RunningUpgradingFeature();
        Prototype prototype = launch2RunningUpgradingFeature.getPrototype();

        // if there are no differences, no need to assemble the new Feature, it is a vanilla Feature

        if (launch2RunningUpgradingFeature.getBundles().isEmpty()
                && launch2RunningUpgradingFeature.getConfigurations().isEmpty()
                && prototype.getBundleRemovals().isEmpty()
                && prototype.getConfigurationRemovals().isEmpty()) {
            return launchFeature;
        }

        ArtifactId runtimeId = newId(RUNTIME_CLASSIFIER);

        BuilderContext context = new BuilderContext(this);

        return assemble(runtimeId, context, launch2RunningUpgradingFeature);
    }

    @Override
    public Feature provide(ArtifactId id) {
        if (launchFeature.getId().equals(id)) {
            return launchFeature;
        }
        return null;
    }

    private ArtifactId newId(String classifier) {
        String groupId = launchFeature.getId().getGroupId();
        String artifactId = launchFeature.getId().getArtifactId();
        String version = launchFeature.getId().getVersion();

        return new ArtifactId(groupId, artifactId, version, classifier, PACKAGING_FEATURE);
    }

}
