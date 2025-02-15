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

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * ConfigurationProperties with prefix {@code parser}. Defaults coming from
 * {@code META-INF/sbm-support-rewrite.properties}
 *
 * @author Fabian Krüger
 */
@ConfigurationProperties(prefix = "spring.rewrite")
public class SpringRewriteProperties {

	/**
	 * Whether to skip parsing maven pom files
	 */
	private boolean skipMavenParsing = false;

	/**
	 * Enable/Disable the MavenPomCache. With {@code false} OpenRewrite's
	 * InMemoryMavenPomCache will be used. With {@code true} a composite cache of
	 * RocksdbMavenPomCache and InMemoryMavenPomCache will be used on 64-Bit system.
	 */
	private boolean pomCacheEnabled = false;

	/**
	 * Defines the cache dir for RocksdbMavenPomCache when {@code parser.pomCacheEnabled}
	 * is {@code true}.
	 */
	private String pomCacheDirectory = Path.of(System.getProperty("user.home"))
		.resolve(".rewrite-cache")
		.toAbsolutePath()
		.normalize()
		.toString();

	/**
	 * Comma-separated list of patterns used to create PathMatcher The pattern should not
	 * contain a leading 'glob:'
	 */
	private Set<String> plainTextMasks = Set.of("*.txt");

	/**
	 * Project resources exceeding this threshold will not be parsed and provided as
	 * org.openrewrite.quark.Quark
	 */
	private int sizeThresholdMb = 10;

	/**
	 * Whether only the current Maven module will be parsed
	 */
	private boolean runPerSubmodule = false;

	/**
	 * Comma-separated list of active Maven profiles
	 */
	private List<String> activeProfiles = List.of("default");

	/**
	 * Comma-separated list of patterns used to create PathMatcher to exclude paths from
	 * being parsed.
	 */
	private Set<String> ignoredPathPatterns = Set.of("**/target/**", "target/**", "**/.idea/**", ".idea/**", ".mvn/**",
			"**/.mvn/**", "**.git/**");

	/**
	 * Whether the discovery should fail on invalid active recipes. TODO: Move to
	 * 'discovery' prefix
	 */
	private boolean failOnInvalidActiveRecipes = true;

	public boolean isSkipMavenParsing() {
		return skipMavenParsing;
	}

	public void setSkipMavenParsing(boolean skipMavenParsing) {
		this.skipMavenParsing = skipMavenParsing;
	}

	public boolean isPomCacheEnabled() {
		return pomCacheEnabled;
	}

	public void setPomCacheEnabled(boolean pomCacheEnabled) {
		this.pomCacheEnabled = pomCacheEnabled;
	}

	public String getPomCacheDirectory() {
		return pomCacheDirectory;
	}

	public void setPomCacheDirectory(String pomCacheDirectory) {
		this.pomCacheDirectory = pomCacheDirectory;
	}

	public Set<String> getPlainTextMasks() {
		return plainTextMasks;
	}

	public void setPlainTextMasks(Set<String> plainTextMasks) {
		this.plainTextMasks = plainTextMasks;
	}

	public int getSizeThresholdMb() {
		return sizeThresholdMb;
	}

	public void setSizeThresholdMb(int sizeThresholdMb) {
		this.sizeThresholdMb = sizeThresholdMb;
	}

	public boolean isRunPerSubmodule() {
		return runPerSubmodule;
	}

	public void setRunPerSubmodule(boolean runPerSubmodule) {
		this.runPerSubmodule = runPerSubmodule;
	}

	public List<String> getActiveProfiles() {
		return activeProfiles;
	}

	public void setActiveProfiles(List<String> activeProfiles) {
		this.activeProfiles = activeProfiles;
	}

	public Set<String> getIgnoredPathPatterns() {
		return ignoredPathPatterns;
	}

	public void setIgnoredPathPatterns(Set<String> ignoredPathPatterns) {
		this.ignoredPathPatterns = ignoredPathPatterns;
	}

	public boolean isFailOnInvalidActiveRecipes() {
		return failOnInvalidActiveRecipes;
	}

	public void setFailOnInvalidActiveRecipes(boolean failOnInvalidActiveRecipes) {
		this.failOnInvalidActiveRecipes = failOnInvalidActiveRecipes;
	}

}
