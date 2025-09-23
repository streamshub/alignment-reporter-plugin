package com.github.k_wall;/*
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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

public class ShadeAnalyzer {
    
    public static class ShadeRelocation {
        private final String pattern;
        private final String shadedPattern;
        
        public ShadeRelocation(String pattern, String shadedPattern) {
            this.pattern = pattern;
            this.shadedPattern = shadedPattern;
        }
        
        public String getPattern() {
            return pattern;
        }
        
        public String getShadedPattern() {
            return shadedPattern;
        }
        
        public String applyRelocation(String originalPackage) {
            if (originalPackage.startsWith(pattern)) {
                return originalPackage.replace(pattern, shadedPattern);
            }
            return originalPackage;
        }
        
        @Override
        public String toString() {
            return String.format("%s -> %s", pattern, shadedPattern);
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ShadeRelocation that = (ShadeRelocation) o;
            return Objects.equals(pattern, that.pattern) && Objects.equals(shadedPattern, that.shadedPattern);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(pattern, shadedPattern);
        }
    }
    
    public static class ShadeConfiguration {
        private final List<ShadeRelocation> relocations;
        private final boolean createDependencyReducedPom;
        private final String moduleName;
        
        public ShadeConfiguration(List<ShadeRelocation> relocations, boolean createDependencyReducedPom, String moduleName) {
            this.relocations = relocations != null ? relocations : new ArrayList<>();
            this.createDependencyReducedPom = createDependencyReducedPom;
            this.moduleName = moduleName;
        }
        
        public List<ShadeRelocation> getRelocations() {
            return relocations;
        }
        
        public boolean isCreateDependencyReducedPom() {
            return createDependencyReducedPom;
        }
        
        public boolean hasRelocations() {
            return !relocations.isEmpty();
        }
        
        public String getModuleName() {
            return moduleName;
        }
    }
    
    private final Log log;
    
    public ShadeAnalyzer(Log log) {
        this.log = log;
    }
    
    public ShadeConfiguration analyzeShadeConfiguration(MavenProject project) {
        Plugin shadePlugin = findShadePlugin(project);
        if (shadePlugin == null) {
            log.debug(String.format("No maven-shade-plugin found in project: %s", project.getArtifactId()));
            return new ShadeConfiguration(new ArrayList<>(), false, project.getArtifactId());
        }
        
        return parseShadeConfiguration(shadePlugin, project.getArtifactId());
    }
    
    public List<ShadeConfiguration> analyzeAllShadeConfigurations(List<MavenProject> projects) {
        List<ShadeConfiguration> configurations = new ArrayList<>();
        
        for (MavenProject project : projects) {
            ShadeConfiguration config = analyzeShadeConfiguration(project);
            if (config.hasRelocations()) {
                configurations.add(config);
                log.debug(String.format("Found shade configuration in project: %s with %d relocations", 
                    project.getArtifactId(), config.getRelocations().size()));
            }
        }
        
        return configurations;
    }
    
    public boolean isArtifactShaded(String groupId, String artifactId, List<ShadeConfiguration> shadeConfigs) {
        // Check both groupId and common package patterns for this artifact
        for (ShadeConfiguration config : shadeConfigs) {
            for (ShadeRelocation relocation : config.getRelocations()) {
                String pattern = relocation.getPattern();
                
                // Check if groupId matches the pattern directly
                if (groupId.startsWith(pattern)) {
                    return true;
                }
                
                // Check if this looks like a known mapping between Maven groupId and Java package
                if (isKnownGroupIdToPackageMapping(groupId, artifactId, pattern)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Checks for known mappings between Maven groupId and Java package names.
     * This handles cases where the Maven groupId differs from the Java package prefix.
     */
    private boolean isKnownGroupIdToPackageMapping(String groupId, String artifactId, String packagePattern) {
        // Handle cases where groupId is same as package (most common)
        if (packagePattern.startsWith(groupId)) {
            return true;
        }
        
        // Handle cases where the package pattern contains the artifactId
        return packagePattern.contains(artifactId);
    }
    
    
    private Plugin findShadePlugin(MavenProject project) {
        if (project.getBuild() == null || project.getBuild().getPlugins() == null) {
            return null;
        }
        
        return project.getBuild().getPlugins().stream()
            .filter(plugin -> "org.apache.maven.plugins".equals(plugin.getGroupId()) 
                           && "maven-shade-plugin".equals(plugin.getArtifactId()))
            .findFirst()
            .orElse(null);
    }
    
    private ShadeConfiguration parseShadeConfiguration(Plugin shadePlugin, String moduleName) {
        List<ShadeRelocation> relocations = new ArrayList<>();
        boolean createDependencyReducedPom = false;
        
        Xpp3Dom configuration = (Xpp3Dom) shadePlugin.getConfiguration();
        if (configuration != null) {
            createDependencyReducedPom = parseCreateDependencyReducedPom(configuration);
            relocations.addAll(parseRelocations(configuration));
        }
        
        if (shadePlugin.getExecutions() != null) {
            for (org.apache.maven.model.PluginExecution execution : shadePlugin.getExecutions()) {
                Xpp3Dom execConfig = (Xpp3Dom) execution.getConfiguration();
                if (execConfig != null) {
                    relocations.addAll(parseRelocations(execConfig));
                }
            }
        }
        
        return new ShadeConfiguration(relocations, createDependencyReducedPom, moduleName);
    }
    
    private boolean parseCreateDependencyReducedPom(Xpp3Dom configuration) {
        Xpp3Dom createReducedPom = configuration.getChild("createDependencyReducedPom");
        if (createReducedPom != null) {
            return Boolean.parseBoolean(createReducedPom.getValue());
        }
        return false;
    }
    
    private List<ShadeRelocation> parseRelocations(Xpp3Dom configuration) {
        List<ShadeRelocation> relocations = new ArrayList<>();
        
        Xpp3Dom relocationsNode = configuration.getChild("relocations");
        if (relocationsNode != null) {
            Xpp3Dom[] relocationNodes = relocationsNode.getChildren("relocation");
            
            for (Xpp3Dom relocationNode : relocationNodes) {
                String pattern = getChildValue(relocationNode, "pattern");
                String shadedPattern = getChildValue(relocationNode, "shadedPattern");
                
                if (pattern != null && shadedPattern != null) {
                    relocations.add(new ShadeRelocation(pattern, shadedPattern));
                    log.debug(String.format("Found relocation: %s -> %s", pattern, shadedPattern));
                }
            }
        }
        
        return relocations;
    }
    
    private String getChildValue(Xpp3Dom parent, String childName) {
        Xpp3Dom child = parent.getChild(childName);
        return child != null ? child.getValue() : null;
    }
}