[![Apache Sling](https://sling.apache.org/res/logos/sling.png)](https://sling.apache.org)

&#32;[![Build Status](https://ci-builds.apache.org/job/Sling/job/modules/job/sling-org-apache-sling-feature-r2f/job/master/badge/icon)](https://ci-builds.apache.org/job/Sling/job/modules/job/sling-org-apache-sling-feature-r2f/job/master/)&#32;[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=apache_sling-org-apache-sling-feature-r2f&metric=coverage)](https://sonarcloud.io/dashboard?id=apache_sling-org-apache-sling-feature-r2f)&#32;[![Sonarcloud Status](https://sonarcloud.io/api/project_badges/measure?project=apache_sling-org-apache-sling-feature-r2f&metric=alert_status)](https://sonarcloud.io/dashboard?id=apache_sling-org-apache-sling-feature-r2f)&#32;[![JavaDoc](https://www.javadoc.io/badge/org.apache.sling/org.apache.sling.feature.r2f.svg)](https://www.javadoc.io/doc/org.apache.sling/org.apache.sling.feature.r2f)&#32;[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.apache.sling/org.apache.sling.feature.r2f/badge.svg)](https://search.maven.org/#search%7Cga%7C1%7Cg%3A%22org.apache.sling%22%20a%3A%22org.apache.sling.feature.r2f%22)&#32;[![feature](https://sling.apache.org/badges/group-feature.svg)](https://github.com/apache/sling-aggregator/blob/master/docs/groups/feature.md) [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

## Running Feature

This is a simple OSGi service which is able to convert, given a `BundleContext` instance, a currently running OSGi container to an Apache Sling Feature Model definition.

APIs are really simple: it is necessary first to obtain the `RuntimeEnvironment2FeatureModel` instance from the OSGi Service Registry, then 

```java
import org.apache.sling.feature.r2f.*;

@Reference
RuntimeEnvironment2FeatureModel generator;

...
Feature runtimeFeature = generator.getRunningFeature();
```

## Please Note

Currently version will include in the generated Feature Model `bundles` and `configurations` only, which are the only informations that can be extracted from a `BundleContext` instance.

## Launch Feature

The `RuntimeEnvironment2FeatureModel` OSGi service is also able to retrieve the (assembled) Feature used to launch the platform:

```java
import org.apache.sling.feature.r2f.*;

@Reference
RuntimeEnvironment2FeatureModel generator;

...
Feature launchFeature = generator.getLaunchFeature();
```

## Upgrade Feature

The `RuntimeEnvironment2FeatureModel` OSGi service is also able to compute the upgrade Feature which prototypes from the Feature used to launch the platform and that targets the runtime Feature:

```java
import org.apache.sling.feature.r2f.*;

@Reference
RuntimeEnvironment2FeatureModel generator;

...
Feature launchFeature = generator.getLaunch2RuntimeUpgradingFeature();
```

## The effective Runtime Feature

Finally, the `RuntimeEnvironment2FeatureModel` OSGi service is also able to compute the real runtime Feature which is assembled from the Feature used to launch the platform and that targets the runtime Feature:

```java
import org.apache.sling.feature.r2f.*;

@Reference
RuntimeEnvironment2FeatureModel generator;

...
Feature launchFeature = generator.getLaunch2RuntimeUpgradingFeature();
```
