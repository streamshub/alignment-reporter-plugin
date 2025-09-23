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

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

public final class ShadeAwareAlignmentReporter {

    public static final class ShadeAlignmentResult {
        private final List<ShadeAnalyzer.ShadeConfiguration> shadeConfigurations;
        private final List<Artifact> shadedArtifacts;
        private final List<Artifact> unshadedArtifacts;
        private final List<ArtifactWithPath> shadedArtifactsWithPaths;

        public ShadeAlignmentResult(final List<ShadeAnalyzer.ShadeConfiguration> configurations,
                                    final List<Artifact> shaded,
                                    final List<Artifact> unshaded) {
            this.shadeConfigurations = configurations;
            this.shadedArtifacts = shaded;
            this.unshadedArtifacts = unshaded;
            this.shadedArtifactsWithPaths = null;
        }

        public ShadeAlignmentResult(final List<ShadeAnalyzer.ShadeConfiguration> configurations,
                                    final List<Artifact> shaded,
                                    final List<Artifact> unshaded,
                                    final List<ArtifactWithPath> shadedWithPaths) {
            this.shadeConfigurations = configurations;
            this.shadedArtifacts = shaded;
            this.unshadedArtifacts = unshaded;
            this.shadedArtifactsWithPaths = shadedWithPaths;
        }

        public List<ShadeAnalyzer.ShadeConfiguration> getShadeConfigurations() {
            return shadeConfigurations;
        }

        public List<Artifact> getShadedArtifacts() {
            return shadedArtifacts;
        }

        public List<Artifact> getUnshadedArtifacts() {
            return unshadedArtifacts;
        }

        public boolean hasShadeConfigurations() {
            return !shadeConfigurations.isEmpty();
        }

        public List<ArtifactWithPath> getShadedArtifactsWithPaths() {
            return shadedArtifactsWithPaths;
        }

        public boolean hasPathInformation() {
            return shadedArtifactsWithPaths != null;
        }
    }

    private final ShadeAnalyzer shadeAnalyzer;
    private final Log log;

    public ShadeAwareAlignmentReporter(final Log logger) {
        this.log = logger;
        this.shadeAnalyzer = new ShadeAnalyzer(log);
    }

    public ShadeAlignmentResult analyzeShadeAlignment(final List<MavenProject> projects,
                                                      final List<Artifact> artifacts,
                                                      final Pattern alignmentPattern) {
        return analyzeShadeAlignment(projects, artifacts, alignmentPattern, null);
    }

    public ShadeAlignmentResult analyzeShadeAlignment(final List<MavenProject> projects,
                                                      final List<Artifact> artifacts,
                                                      final Pattern alignmentPattern,
                                                      final List<ArtifactWithPath> artifactsWithPaths) {

        List<ShadeAnalyzer.ShadeConfiguration> shadeConfigs = shadeAnalyzer.analyzeAllShadeConfigurations(projects);

        List<Artifact> shadedArtifacts = artifacts.stream()
            .filter(artifact -> shadeAnalyzer.isArtifactShaded(artifact.getGroupId(), artifact.getArtifactId(), shadeConfigs))
            .collect(java.util.stream.Collectors.toList());

        List<Artifact> unshadedArtifacts = artifacts.stream()
            .filter(artifact -> !shadeAnalyzer.isArtifactShaded(artifact.getGroupId(), artifact.getArtifactId(), shadeConfigs))
            .collect(java.util.stream.Collectors.toList());

        // Filter artifactsWithPaths to only include shaded ones
        List<ArtifactWithPath> shadedArtifactsWithPaths = null;
        if (artifactsWithPaths != null) {
            shadedArtifactsWithPaths = artifactsWithPaths.stream()
                .filter(awp -> shadeAnalyzer.isArtifactShaded(awp.getArtifact()
                    .getGroupId(), awp.getArtifact()
                    .getArtifactId(), shadeConfigs))
                .collect(java.util.stream.Collectors.toList());
        }

        return new ShadeAlignmentResult(shadeConfigs, shadedArtifacts, unshadedArtifacts, shadedArtifactsWithPaths);
    }

    public String generateShadeReport(final ShadeAlignmentResult result, final Pattern alignmentPattern) throws IOException {
        try (StringWriter out = new StringWriter(); PrintWriter writer = new PrintWriter(out)) {

            if (!result.hasShadeConfigurations()) {
                writer.println("No shade configurations found in the project.");
                writer.println();
                return out.toString();
            }

            writer.println("Shade Configuration Analysis");
            writer.println("===========================");
            writer.println();

            writer.println(String.format("Found %d shade configuration(s)", result.getShadeConfigurations()
                .size()));
            writer.println();

            int configIndex = 1;
            for (ShadeAnalyzer.ShadeConfiguration config : result.getShadeConfigurations()) {
                writer.println(String.format("Configuration #%d (%s):", configIndex++, config.getModuleName()));
                writer.println(String.format("  - Create dependency reduced POM: %s", config.isCreateDependencyReducedPom()));
                writer.println(String.format("  - Number of relocations: %d", config.getRelocations()
                    .size()));

                if (!config.getRelocations()
                    .isEmpty()) {
                    writer.println("  - Relocations:");
                    for (ShadeAnalyzer.ShadeRelocation relocation : config.getRelocations()) {
                        writer.println(String.format("    * %s", relocation.toString()));
                    }
                }
                writer.println();
            }

            // Show both aligned and unaligned shaded artifacts, similar to regular dependency reports
            List<Artifact> alignedShadedArtifacts = result.getShadedArtifacts()
                .stream()
                .filter(artifact -> alignmentPattern.matcher(artifact.getVersion()).find())
                .collect(java.util.stream.Collectors.toList());

            List<Artifact> unalignedShadedArtifacts = result.getShadedArtifacts()
                .stream()
                .filter(artifact -> !alignmentPattern.matcher(artifact.getVersion()).find())
                .collect(java.util.stream.Collectors.toList());

            if (!alignedShadedArtifacts.isEmpty()) {
                String title = String.format("%d Aligned artifacts affected by shading", alignedShadedArtifacts.size());
                writer.println(title);
                writer.println("-".repeat(title.length()));
                for (Artifact artifact : alignedShadedArtifacts) {
                    writer.println(String.format("Aligned - %s", artifact));
                }
                writer.println();
            }

            if (!unalignedShadedArtifacts.isEmpty()) {
                String title = String.format("%d Unaligned artifacts affected by shading", unalignedShadedArtifacts.size());
                writer.println(title);
                writer.println("-".repeat(title.length()));
                for (Artifact artifact : unalignedShadedArtifacts) {
                    writer.println(String.format("Unaligned - %s", artifact));
                }
                writer.println();
            }

            return out.toString();
        }
    }

    public String generateShadeAlignmentSummary(final ShadeAlignmentResult result, final Pattern alignmentPattern) throws IOException {
        try (StringWriter out = new StringWriter(); PrintWriter writer = new PrintWriter(out)) {

            if (!result.hasShadeConfigurations()) {
                return "";
            }

            writer.println("Shade-Aware Alignment Summary");
            writer.println("============================");
            writer.println();

            long alignedShaded = result.getShadedArtifacts()
                .stream()
                .filter(artifact -> alignmentPattern.matcher(artifact.getVersion())
                    .find())
                .count();

            long unalignedShaded = result.getShadedArtifacts()
                .size() - alignedShaded;

            writer.println(String.format("Shaded artifacts: %d total, %d aligned, %d unaligned",
                result.getShadedArtifacts()
                    .size(), alignedShaded, unalignedShaded));

            if (unalignedShaded > 0) {
                writer.println();
                writer.println("Unaligned Shaded Artifacts:");
                writer.println("---------------------------");
                result.getShadedArtifacts()
                    .stream()
                    .filter(artifact -> !alignmentPattern.matcher(artifact.getVersion())
                        .find())
                    .forEach(artifact -> writer.println(String.format("Unaligned - %s", artifact)));
            }

            // Add path information if available
            if (result.hasPathInformation() && !result.getShadedArtifactsWithPaths()
                .isEmpty()) {
                writer.println();
                writer.println("Shaded Artifacts with Dependency Paths");
                writer.println("--------------------------------------");

                // Group by alignment status and dependency type
                List<ArtifactWithPath> transitiveAlignedShaded = result.getShadedArtifactsWithPaths()
                    .stream()
                    .filter(ArtifactWithPath::isTransitive)
                    .filter(awp -> alignmentPattern.matcher(awp.getArtifact().getVersion()).find())
                    .sorted(Comparator.comparing(a -> a.getArtifact()
                        .toString()))
                    .collect(java.util.stream.Collectors.toList());

                List<ArtifactWithPath> transitiveUnalignedShaded = result.getShadedArtifactsWithPaths()
                    .stream()
                    .filter(ArtifactWithPath::isTransitive)
                    .filter(awp -> !alignmentPattern.matcher(awp.getArtifact().getVersion()).find())
                    .sorted(Comparator.comparing(a -> a.getArtifact()
                        .toString()))
                    .collect(java.util.stream.Collectors.toList());

                if (!transitiveAlignedShaded.isEmpty()) {
                    writer.println();
                    writer.println("Direct Aligned Shaded Artifacts:");
                    writer.println("---------------------------------");
                    for (ArtifactWithPath awp : transitiveAlignedShaded) {
                        writer.println(String.format("Aligned - %s", awp.getArtifact()));
                    }
                }

                if (!transitiveUnalignedShaded.isEmpty()) {
                    writer.println();
                    writer.println("Direct Unaligned Shaded Artifacts:");
                    writer.println("----------------------------------");
                    for (ArtifactWithPath awp : transitiveUnalignedShaded) {
                        writer.println(String.format("Unaligned - %s", awp.getArtifact()));
                    }
                }

                if (!transitiveAlignedShaded.isEmpty()) {
                    writer.println();
                    writer.println("Transitive Aligned Shaded Artifacts:");
                    writer.println("------------------------------------");
                    for (ArtifactWithPath awp : transitiveAlignedShaded) {
                        writer.println(String.format("Aligned - %s", awp.formatDependencyPath()));
                    }
                }

                if (!transitiveUnalignedShaded.isEmpty()) {
                    writer.println();
                    writer.println("Transitive Unaligned Shaded Artifacts:");
                    writer.println("--------------------------------------");
                    for (ArtifactWithPath awp : transitiveUnalignedShaded) {
                        writer.println(String.format("Unaligned - %s", awp.formatDependencyPath()));
                    }
                }
            }

            writer.println();
            return out.toString();
        }
    }
}