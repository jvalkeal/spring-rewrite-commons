/*
 * Copyright 2021 - 2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.rewrite.parsers;

import org.openrewrite.ExecutionContext;
import org.openrewrite.maven.cache.CompositeMavenPomCache;
import org.openrewrite.maven.cache.InMemoryMavenPomCache;
import org.openrewrite.maven.cache.MavenPomCache;
import org.openrewrite.maven.cache.RocksdbMavenPomCache;
import org.openrewrite.maven.utilities.MavenArtifactDownloader;
import org.openrewrite.tree.ParsingEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ResourceLoader;
import org.springframework.rewrite.boot.autoconfigure.ScopeConfiguration;
import org.springframework.rewrite.parsers.events.RewriteParsingEventListenerAdapter;
import org.springframework.rewrite.parsers.maven.*;
import org.springframework.rewrite.project.resource.SbmApplicationProperties;
import org.springframework.rewrite.scopes.annotations.ScanScope;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Module configuration.
 *
 * @author Fabian Krüger
 */
@AutoConfiguration(after = { ScopeConfiguration.class })
@EnableConfigurationProperties({ SpringRewriteProperties.class, SbmApplicationProperties.class })
@Import({ org.springframework.rewrite.scopes.ScanScope.class, ScopeConfiguration.class,
		RewriteParserMavenConfiguration.class })
public class RewriteParserConfiguration {

	private static final Logger LOGGER = LoggerFactory.getLogger(RewriteParserConfiguration.class);

	@Bean
	ProjectScanner projectScanner(ResourceLoader resourceLoader, SpringRewriteProperties springRewriteProperties) {
		return new ProjectScanner(resourceLoader, springRewriteProperties);
	}

	@Bean
	ProvenanceMarkerFactory provenanceMarkerFactory(MavenProvenanceMarkerFactory mavenPovenanceMarkerFactory) {
		return new ProvenanceMarkerFactory(mavenPovenanceMarkerFactory);
	}

	@Bean
	@ScanScope
	JavaParserBuilder javaParserBuilder() {
		return new JavaParserBuilder();
	}

	@Bean
	Consumer<Throwable> artifactDownloaderErrorConsumer() {
		return (t) -> {
			throw new RuntimeException(t);
		};
	}

	@Bean
	ModuleParser moduleParser() {
		return new ModuleParser();
	}

	@Bean
	MavenModuleParser mavenModuleParser(SpringRewriteProperties springRewriteProperties, ModuleParser moduleParser) {
		return new MavenModuleParser(springRewriteProperties, moduleParser);
	}

	@Bean
	SourceFileParser sourceFileParser(MavenModuleParser mavenModuleParser) {
		return new SourceFileParser(mavenModuleParser);
	}

	@Bean
	StyleDetector styleDetector() {
		return new StyleDetector();
	}

	@Bean
	@ConditionalOnMissingBean(ParsingEventListener.class)
	ParsingEventListener parsingEventListener(ApplicationEventPublisher eventPublisher) {
		return new RewriteParsingEventListenerAdapter(eventPublisher);
	}

	@Bean
	MavenProjectAnalyzer mavenProjectAnalyzer(MavenArtifactDownloader artifactDownloader) {
		return new MavenProjectAnalyzer(artifactDownloader);
	}

	@Bean
	RewriteProjectParser rewriteProjectParser(ProvenanceMarkerFactory provenanceMarkerFactory,
			BuildFileParser buildFileParser, SourceFileParser sourceFileParser, StyleDetector styleDetector,
			SpringRewriteProperties springRewriteProperties, ParsingEventListener parsingEventListener,
			ApplicationEventPublisher eventPublisher, org.springframework.rewrite.scopes.ScanScope scanScope,
			ConfigurableListableBeanFactory beanFactory, ProjectScanner projectScanner,
			ExecutionContext executionContext, MavenProjectAnalyzer mavenProjectAnalyzer) {
		return new RewriteProjectParser(provenanceMarkerFactory, buildFileParser, sourceFileParser, styleDetector,
				springRewriteProperties, parsingEventListener, eventPublisher, scanScope, beanFactory, projectScanner,
				executionContext, mavenProjectAnalyzer);
	}

	@Bean
	@ConditionalOnMissingBean(MavenPomCache.class)
	MavenPomCache mavenPomCache(SpringRewriteProperties springRewriteProperties) {
		MavenPomCache mavenPomCache = new InMemoryMavenPomCache();
		if (springRewriteProperties.isPomCacheEnabled()) {
			if (!"64".equals(System.getProperty("sun.arch.data.model", "64"))) {
				LOGGER.warn(
						"parser.isPomCacheEnabled was set to true but RocksdbMavenPomCache is not supported on 32-bit JVM. falling back to InMemoryMavenPomCache");
			}
			else {
				try {
					mavenPomCache = new CompositeMavenPomCache(new InMemoryMavenPomCache(),
							new RocksdbMavenPomCache(Path.of(springRewriteProperties.getPomCacheDirectory())));
				}
				catch (Exception e) {
					LOGGER.warn("Unable to initialize RocksdbMavenPomCache, falling back to InMemoryMavenPomCache");
					if (LOGGER.isDebugEnabled()) {
						StringWriter sw = new StringWriter();
						e.printStackTrace(new PrintWriter(sw));
						String exceptionAsString = sw.toString();
						LOGGER.debug(exceptionAsString);
					}
				}
			}
		}
		return mavenPomCache;
	}

}
