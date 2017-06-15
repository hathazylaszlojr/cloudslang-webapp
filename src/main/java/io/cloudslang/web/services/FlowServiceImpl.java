/*******************************************************************************
 * (c) Copyright 2017 Hewlett-Packard Development Company, L.P.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License v2.0 which accompany this distribution.
 *
 * The Apache License is available at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 *******************************************************************************/
package io.cloudslang.web.services;

import io.cloudslang.lang.api.Slang;
import io.cloudslang.lang.commons.services.api.SlangCompilationService;
import io.cloudslang.lang.compiler.Extension;
import io.cloudslang.lang.compiler.SlangSource;
import io.cloudslang.lang.entities.CompilationArtifact;
import io.cloudslang.lang.entities.bindings.Input;
import io.cloudslang.web.client.FlowInputVo;
import io.cloudslang.web.client.FlowVo;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import javax.annotation.PostConstruct;

@Service
public class FlowServiceImpl implements FlowService {

    private static final String FLOW_TAG = "flow:";

    private static final String WORKFLOW_TAG = "workflow:";

    @Value("${content.path}")
    private String contentPath;

    @Autowired
    private SlangCompilationService slangCompilationService;

    @Autowired
    private Slang slang;

    private Map<String, String> flowMap;

    @PostConstruct
    public void populateFlowMap() throws IOException {
        flowMap = new HashMap<>();
        File dir = new File(contentPath);
        Collection<File> slangFiles = slangCompilationService.listSlangFiles(dir, true);

        for (File file : slangFiles) {
            if (fileIsFlow(file)) {
                flowMap.put(filePathToFlowId(file.getPath()), file.getPath());
            }
        }
    }

    public List<FlowVo> getAllFlows() throws IOException {
        List<FlowVo> flows = new ArrayList<>();
        for (String filename : flowMap.values()) {
            flows.add(new FlowVo(filePathToFlowName(filename), filePathToFlowId(filename)));
        }
        return flows;
    }

    private boolean fileIsFlow(File file) throws IOException {
        if (!(extensionStartIndex(file.getPath()) > 0)) {
            return false;
        }

        List<String> lines = Files.readAllLines(Paths.get(file.getAbsolutePath()), SlangSource.getCloudSlangCharset());
        for (String line : lines) {
            if (line.contains(FLOW_TAG) && !line.contains(WORKFLOW_TAG)) {
                return true;
            }
        }
        return false;
    }

    public List<FlowInputVo> getInputsForFlow(String flowId) {
        File file = slangCompilationService.getFile(flowIdToFilePath(flowId));

        CompilationArtifact compilationArtifact = slang.compile(SlangSource.fromFile(file),
            this.getDependencySources(Collections.singletonList(contentPath), file));

        List<Input> inputs = compilationArtifact.getInputs();
        List<FlowInputVo> inputsResult = new ArrayList<>();
        for (Input input : inputs) {
            if (!input.isPrivateInput()) {
                inputsResult.add(new FlowInputVo(input));
            }
        }
        return inputsResult;
    }

    private Set<SlangSource> getDependencySources(List<String> dependencies, File file) {
        dependencies = getDependenciesIfEmpty(dependencies, file);
        return slangCompilationService.getSourcesFromFolders(dependencies);
    }

    private List<String> getDependenciesIfEmpty(List<String> dependencies, File file) {
        if (CollectionUtils.isEmpty((Collection) dependencies)) {
            dependencies = new ArrayList();
            String appHome = System.getProperty("app.home", "");
            String contentRoot = appHome + File.separator + "content";
            File contentRootDir = new File(contentRoot);
            if (StringUtils.isNotEmpty(appHome) && contentRootDir.exists() && contentRootDir.isDirectory()) {
                ((List) dependencies).add(contentRoot);
            } else {
                ((List) dependencies).add(file.getParent());
            }
        }

        return (List) dependencies;
    }

    private String filePathToFlowId(String filePath) {
        return filePath
            .substring(contentPath.length(), filePath.indexOf("."))
            .replace(contentPath + File.separator, "")
            .replace(File.separator, ".");
    }

    private String filePathToFlowName(String filePath) {
        if (extensionStartIndex(filePath) >= 0) {
            return filePath.substring(filePath.lastIndexOf(File.separator) + 1, extensionStartIndex(filePath));
        }
        return null;
    }

    private int extensionStartIndex(String filePath) {
        String[] slangFileExtensions = Extension.getSlangFileExtensionValues();
        for (String extension : slangFileExtensions) {
            if (filePath.endsWith("." + extension)) {
                return filePath.lastIndexOf("." + extension);
            }
        }
        return -1;
    }

    String flowIdToFilePath(String flowId) {
        return flowMap.get(flowId);
    }

    String getContentPath() {
        return contentPath;
    }
}