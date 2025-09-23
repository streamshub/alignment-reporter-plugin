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

import java.util.Set;

import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.shared.dependency.graph.DependencyNode;

@Mojo(name = "report", requiresDependencyCollection = ResolutionScope.TEST, threadSafe = true)
public class AlignmentReporterMojo extends AbstractAlignmentReporterMojo {
    @Override
    protected Set<DependencyNode> getDirectDependencies(final ArtifactFilter artifactFilter) throws MojoExecutionException {
        return getDirectDependencies(getProject(), artifactFilter);
    }
}
