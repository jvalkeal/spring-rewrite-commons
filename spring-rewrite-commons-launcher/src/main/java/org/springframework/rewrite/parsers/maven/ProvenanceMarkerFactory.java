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

import org.openrewrite.marker.Marker;
import org.springframework.core.io.Resource;
import org.springframework.rewrite.parsers.ParserContext;
import org.springframework.rewrite.utils.ResourceUtil;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Fabian Krüger
 */
public class ProvenanceMarkerFactory {

	private final MavenProvenanceMarkerFactory markerFactory;

	public ProvenanceMarkerFactory(MavenProvenanceMarkerFactory markerFactory) {
		this.markerFactory = markerFactory;
	}

	/**
	 * Create Provenance markers
	 * @return the map of pom.xml {@link Resource}s and their {@link Marker}s.
	 */
	public Map<Path, List<Marker>> generateProvenanceMarkers(Path baseDir, ParserContext parserContext) {

		Map<Path, List<Marker>> result = new HashMap<>();

		parserContext.getSortedProjects().forEach(mavenProject -> {

			List<Marker> markers = markerFactory.generateProvenance(baseDir, mavenProject);
			Resource resource = parserContext.getMatchingBuildFileResource(mavenProject);
			Path path = ResourceUtil.getPath(resource);
			result.put(path, markers);
		});
		return result;
	}

}
