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
package org.springframework.rewrite.parsers.maven;

import org.openrewrite.ExecutionContext;
import org.openrewrite.SourceFile;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.marker.Marker;
import org.openrewrite.style.NamedStyles;
import org.openrewrite.tree.ParsingExecutionContextView;
import org.openrewrite.xml.tree.Xml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.rewrite.parsers.*;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Fabian Krüger
 */
public class MavenModuleParser {

	private static final Logger LOGGER = LoggerFactory.getLogger(MavenProvenanceMarkerFactory.class);

	private final SpringRewriteProperties springRewriteProperties;

	private final ModuleParser mavenMojoProjectParserPrivateMethods;

	public MavenModuleParser(SpringRewriteProperties springRewriteProperties,
			ModuleParser mavenMojoProjectParserPrivateMethods) {
		this.springRewriteProperties = springRewriteProperties;
		this.mavenMojoProjectParserPrivateMethods = mavenMojoProjectParserPrivateMethods;
	}

	public List<SourceFile> parseModuleSourceFiles(List<Resource> resources, MavenProject currentProject,
			Xml.Document moduleBuildFile, List<Marker> provenanceMarkers, List<NamedStyles> styles,
			ExecutionContext executionContext, Path baseDir) {

		List<SourceFile> sourceFiles = new ArrayList<>();
		// 146:149: get source encoding from maven
		// TDOD:
		// String s =
		// moduleBuildFile.getMarkers().findFirst(MavenResolutionResult.class).get().getPom().getProperties().get("project.build.sourceEncoding");
		// if (mavenSourceEncoding != null) {
		// ParsingExecutionContextView.view(ctx).setCharset(Charset.forName(mavenSourceEncoding.toString()));
		// }
		Object mavenSourceEncoding = currentProject.getProjectEncoding();
		if (mavenSourceEncoding != null) {
			ParsingExecutionContextView.view(executionContext)
				.setCharset(Charset.forName(mavenSourceEncoding.toString()));
		}

		JavaParser.Builder<? extends JavaParser, ?> javaParserBuilder = JavaParser.fromJavaVersion()
			.styles(styles)
			.logCompilationWarningsAndErrors(false);

		Path buildFilePath = currentProject.getBasedir().resolve(moduleBuildFile.getSourcePath());
		LOGGER.info("Parsing module " + buildFilePath);
		// these paths will be ignored by ResourceParser
		Set<Path> skipResourceScanDirs = pathsToOtherMavenProjects(currentProject, buildFilePath);
		RewriteResourceParser rp = new RewriteResourceParser(baseDir, springRewriteProperties.getIgnoredPathPatterns(),
				springRewriteProperties.getPlainTextMasks(), springRewriteProperties.getSizeThresholdMb(),
				skipResourceScanDirs, javaParserBuilder.clone(), executionContext);

		Set<Path> alreadyParsed = new HashSet<>();
		Path moduleBuildFilePath = baseDir.resolve(moduleBuildFile.getSourcePath());
		alreadyParsed.add(moduleBuildFilePath);
		alreadyParsed.addAll(skipResourceScanDirs);
		SourceSetParsingResult mainSourcesParsingResult = parseMainSources(baseDir, currentProject, moduleBuildFile,
				resources, javaParserBuilder.clone(), rp, provenanceMarkers, alreadyParsed, executionContext);
		SourceSetParsingResult testSourcesParsingResult = parseTestSources(baseDir, currentProject, moduleBuildFile,
				javaParserBuilder.clone(), rp, provenanceMarkers, alreadyParsed, executionContext, resources,
				mainSourcesParsingResult.classpath());
		// Collect the dirs of modules parsed in previous steps

		// parse other project resources
		Stream<SourceFile> parsedResourceFiles = rp.parse(moduleBuildFilePath.getParent(), resources, alreadyParsed)
			// FIXME: handle generated sources
			.map(mavenMojoProjectParserPrivateMethods.addProvenance(baseDir, provenanceMarkers, null));

		List<SourceFile> mainAndTestSources = mergeAndFilterExcluded(baseDir,
				springRewriteProperties.getIgnoredPathPatterns(), mainSourcesParsingResult.sourceFiles(),
				testSourcesParsingResult.sourceFiles());
		List<SourceFile> resourceFilesList = parsedResourceFiles.toList();
		sourceFiles.addAll(mainAndTestSources);
		sourceFiles.addAll(resourceFilesList);

		return sourceFiles;
	}

	private List<SourceFile> mergeAndFilterExcluded(Path baseDir, Set<String> exclusions, List<SourceFile> mainSources,
			List<SourceFile> testSources) {
		List<PathMatcher> pathMatchers = exclusions.stream()
			.map(pattern -> baseDir.getFileSystem().getPathMatcher("glob:" + pattern))
			.toList();
		if (pathMatchers.isEmpty()) {
			return Stream.concat(mainSources.stream(), testSources.stream()).toList();
		}
		return new ArrayList<>(Stream.concat(mainSources.stream(), testSources.stream())
			.filter(s -> isNotExcluded(baseDir, pathMatchers, s))
			.toList());
	}

	private static boolean isNotExcluded(Path baseDir, List<PathMatcher> exclusions, SourceFile s) {
		return exclusions.stream()
			.noneMatch(pm -> pm.matches(baseDir.resolve(s.getSourcePath()).toAbsolutePath().normalize()));
	}

	private SourceSetParsingResult parseTestSources(Path baseDir, MavenProject mavenProject,
			Xml.Document moduleBuildFile, JavaParser.Builder<? extends JavaParser, ?> javaParserBuilder,
			RewriteResourceParser rp, List<Marker> provenanceMarkers, Set<Path> alreadyParsed,
			ExecutionContext executionContext, List<Resource> resources, List<JavaType.FullyQualified> classpath) {
		return mavenMojoProjectParserPrivateMethods.processTestSources(baseDir, moduleBuildFile, javaParserBuilder, rp,
				provenanceMarkers, alreadyParsed, executionContext, mavenProject, resources, classpath);
	}

	/**
	 *
	 */
	private SourceSetParsingResult parseMainSources(Path baseDir, MavenProject mavenProject,
			Xml.Document moduleBuildFile, List<Resource> resources,
			JavaParser.Builder<? extends JavaParser, ?> javaParserBuilder, RewriteResourceParser rp,
			List<Marker> provenanceMarkers, Set<Path> alreadyParsed, ExecutionContext executionContext) {

		return mavenMojoProjectParserPrivateMethods.processMainSources(baseDir, resources, moduleBuildFile,
				javaParserBuilder, rp, provenanceMarkers, alreadyParsed, executionContext, mavenProject);
	}

	private Set<Path> pathsToOtherMavenProjects(MavenProject mavenProject, Path moduleBuildFile) {
		return mavenProject.getCollectedProjects()
			.stream()
			.filter(p -> !p.getFile().toPath().toString().equals(moduleBuildFile.toString()))
			.map(p -> p.getFile().toPath().getParent())
			.collect(Collectors.toSet());
	}

}
