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

import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Parser;
import org.openrewrite.SourceFile;
import org.openrewrite.tree.ParsingEventListener;
import org.openrewrite.tree.ParsingExecutionContextView;
import org.sonatype.plexus.components.cipher.PlexusCipherException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.rewrite.parsers.maven.*;
import org.springframework.rewrite.scopes.ProjectMetadata;
import org.springframework.rewrite.scopes.ScanScope;
import org.springframework.rewrite.test.util.DummyResource;
import org.springframework.rewrite.utils.ResourceUtil;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * @author Fabian Krüger
 */
class RewriteProjectParserTest {

	@Language("xml")
	String pomXml = """
			<?xml version="1.0" encoding="UTF-8"?>
			<project xmlns="http://maven.apache.org/POM/4.0.0"
			         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
			         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
			    <modelVersion>4.0.0</modelVersion>
			    <groupId>org.example</groupId>
			    <artifactId>root-project</artifactId>
			    <version>1.0.0</version>
			    <properties>
			        <maven.compiler.target>17</maven.compiler.target>
			        <maven.compiler.source>17</maven.compiler.source>
			    </properties>
			    <dependencies>
			        <dependency>
			            <groupId>org.springframework.boot</groupId>
			            <artifactId>spring-boot-starter</artifactId>
			            <version>3.1.1</version>
			        </dependency>
			    </dependencies>
			</project>
			""";

	@Language("java")
	String javaClass = """
			package com.example;
			import org.springframework.boot.SpringApplication;
			import org.springframework.boot.autoconfigure.SpringBootApplication;

			@SpringBootApplication
			public class MyMain {
			    public static void main(String[] args){
			        SpringApplication.run(MyMain.class, args);
			    }
			}
			""";

	@Test
	@DisplayName("Parse simple Maven project")
	void parseSimpleMavenProject(@TempDir Path tempDir) throws PlexusCipherException {
		Path basePath = tempDir;
		SpringRewriteProperties springRewriteProperties = new SpringRewriteProperties();
		ModuleParser mavenMojoParserPrivateMethods = new ModuleParser();
		ExecutionContext executionContext = new InMemoryExecutionContext(t -> {
			throw new RuntimeException(t);
		});
		MavenModuleParser mavenModuleParser = new MavenModuleParser(springRewriteProperties,
				mavenMojoParserPrivateMethods);
		ProjectMetadata projectMetadata = new ProjectMetadata();
		MavenSettingsInitializer mavenSettingsInitializer = new MavenSettingsInitializer(executionContext,
				projectMetadata);
		RewriteProjectParser projectParser = new RewriteProjectParser(
				new ProvenanceMarkerFactory(new MavenProvenanceMarkerFactory()),
				new BuildFileParser(mavenSettingsInitializer), new SourceFileParser(mavenModuleParser),
				new StyleDetector(), springRewriteProperties, mock(ParsingEventListener.class),
				mock(ApplicationEventPublisher.class), new ScanScope(), mock(ConfigurableListableBeanFactory.class),
				new ProjectScanner(new DefaultResourceLoader(), springRewriteProperties), executionContext,
				new MavenProjectAnalyzer(mock(RewriteMavenArtifactDownloader.class)));

		List<String> parsedFiles = new ArrayList<>();
		ParsingExecutionContextView.view(executionContext).setParsingListener(new ParsingEventListener() {
			@Override
			public void parsed(Parser.Input input, SourceFile sourceFile) {
				parsedFiles.add(sourceFile.getSourcePath().toString());
			}
		});

		// TODO: Provide Scanner with excludes
		// TODO: Make RewriteProjectParser publish ApplicationEvents
		List<Resource> resources = List.of(new DummyResource(basePath.resolve("pom.xml"), pomXml),
				new DummyResource(basePath.resolve("src/main/java/com/example/MyMain.java"), javaClass));
		ResourceUtil.write(basePath, resources);

		RewriteProjectParsingResult parsingResult = projectParser.parse(basePath, resources);

		assertThat(parsingResult.sourceFiles()).hasSize(2);
	}

}