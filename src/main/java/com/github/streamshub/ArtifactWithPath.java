/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.github.streamshub;

import java.util.List;
import java.util.Objects;

import org.apache.maven.artifact.Artifact;

/**
 * Represents an artifact along with its dependency path.
 * Used for tracking how transitive dependencies are reached in the dependency tree.
 */
public final class ArtifactWithPath {
    private final Artifact artifact;
    private final List<Artifact> dependencyPath;
    private final boolean isDirect;

    /**
     * Creates an ArtifactWithPath for a direct dependency.
     *
     * @param artifact the artifact
     */
    public ArtifactWithPath(final Artifact artifactParam) {
        this.artifact = artifactParam;
        this.dependencyPath = List.of(artifactParam);
        this.isDirect = true;
    }

    /**
     * Creates an ArtifactWithPath for a transitive dependency.
     *
     * @param artifact       the artifact
     * @param dependencyPath the full path from root to this artifact (including this artifact)
     */
    public ArtifactWithPath(final Artifact artifactParam, final List<Artifact> pathParam) {
        this.artifact = artifactParam;
        this.dependencyPath = List.copyOf(pathParam);
        this.isDirect = pathParam.size() == 1;
    }

    public Artifact getArtifact() {
        return artifact;
    }

    public List<Artifact> getDependencyPath() {
        return dependencyPath;
    }

    public boolean isDirect() {
        return isDirect;
    }

    public boolean isTransitive() {
        return !isDirect;
    }

    /**
     * Returns the root dependency (the direct dependency that brought this artifact in).
     * For direct dependencies, returns the artifact itself.
     */
    public Artifact getRootDependency() {
        return dependencyPath.get(0);
    }

    /**
     * Formats the dependency path as a string in the format:
     * "artifact <- parent <- ... <- root"
     */
    public String formatDependencyPath() {
        if (isDirect) {
            return artifact.toString() + " (direct)";
        }

        StringBuilder sb = new StringBuilder();

        // Add the current artifact first
        sb.append(artifact.toString());

        // Add the path in reverse order (excluding the current artifact)
        for (int i = dependencyPath.size() - 2; i >= 0; i--) {
            sb.append(" <- ");
            sb.append(dependencyPath.get(i)
                .toString());
        }

        return sb.toString();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ArtifactWithPath that = (ArtifactWithPath) o;
        return Objects.equals(artifact, that.artifact);
    }

    @Override
    public int hashCode() {
        return Objects.hash(artifact);
    }

    @Override
    public String toString() {
        return formatDependencyPath();
    }
}