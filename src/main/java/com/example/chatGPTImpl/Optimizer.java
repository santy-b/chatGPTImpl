package com.example.chatGPTImpl;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.ToolProvider;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Optimizer {
    private String model;
    private String userName;
    private String password;
    private Logger logger = Logger.getLogger(Optimizer.class.getName());
    private ApiHttpsEmailClient client = new ApiHttpsEmailClient(logger);
    public Optimizer(String model, String userName, String password) {
        this.model = model;
        this.userName = userName;
        this.password = password;
    }

    public void optimizeCodeAndEmail(String repositoryUrl, String recipientEmail) {
        File repositoryDirectory = null;
        try {
            repositoryDirectory = client.downloadRepository(repositoryUrl);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        List<File> javaFiles = findJavaFiles(repositoryDirectory);
        for (File javaFile : javaFiles) {
            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            JavaCompiler.CompilationTask task = compileJavaFile(javaFile, diagnostics);
            boolean success = task.call();
            String errors = getErrorMessages(success, diagnostics);

            // Find corresponding pom.xml file for the Java file
            File pomFile = findPomFile(javaFile.getName(), repositoryDirectory);
            String[] prompt = {optimizePrompt()[0], optimizePrompt()[1] + "\n" + errors, optimizePrompt()[2] + "\n" + getPomDependencies(pomFile)};
            String solution = null;
            try {
                solution = client.apiRequest(javaFile.getAbsolutePath(), prompt, model);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            System.out.println("SOLUTION");
            System.out.println(solution);
            client.sendEmail(recipientEmail, solution, javaFile.getName(), userName, password);
        }
    }

    private List<File> findJavaFiles(File repositoryDirectory) {
        List<File> javaFiles = new ArrayList<>();
        File[] files = repositoryDirectory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory() && !file.getName().startsWith(".")) {
                    javaFiles.addAll(findJavaFiles(file));
                } else if (file.getName().endsWith(".java")) {
                    javaFiles.add(file);
                }
            }
        }
        return javaFiles;
    }

    private File findPomFile(String javaFileName, File directory) {
        File javaFile = new File(directory, javaFileName);
        File pomFile = new File(directory, "pom.xml");
        while (!pomFile.exists() && !javaFile.getParentFile().equals(directory.getParentFile())) {
            directory = directory.getParentFile();
            pomFile = new File(directory, "pom.xml");
        }
        return pomFile.exists() ? pomFile : null;
    }

    private List<String> getPomDependencies(File pomFile) {
        List<String> dependencies = new ArrayList<>();
        try {
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
            Document document = documentBuilder.parse(pomFile);
            String javaVersion = document.getElementsByTagName("java.version").item(0).getTextContent();
            NodeList dependencyList = document.getElementsByTagName("dependency");
            for (int i = 0; i < dependencyList.getLength(); i++) {
                Element dependency = (Element) dependencyList.item(i);
                NodeList groupId = dependency.getElementsByTagName("groupId");
                NodeList artifactId = dependency.getElementsByTagName("artifactId");
                NodeList version = dependency.getElementsByTagName("version");
                if (groupId.getLength() > 0 && artifactId.getLength() > 0 && version.getLength() > 0) {
                    String groupIdStr = groupId.item(0) != null ? groupId.item(0).getTextContent() : "";
                    String artifactIdStr = artifactId.item(0) != null ? artifactId.item(0).getTextContent() : "";
                    String versionStr = version.item(0) != null ? version.item(0).getTextContent() : "";
                    String dependencyString = groupIdStr + ":" + artifactIdStr + ":" + versionStr;
                    dependencies.add(dependencyString);
                }
            }
            if (javaVersion != null && !javaVersion.isEmpty()) {
                dependencies.add("java.version:" + javaVersion);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error parsing POM file: " + e.getMessage(), e);
        }
        return dependencies;
    }

    private JavaCompiler.CompilationTask compileJavaFile(File javaFile, DiagnosticCollector<JavaFileObject> diagnostics) {
        try {
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null);
            Iterable<? extends JavaFileObject> fileObjects = fileManager.getJavaFileObjects(javaFile);
            return compiler.getTask(null, fileManager, diagnostics, null, null, fileObjects);
        } catch (Exception e) {
            System.err.println("Error compiling Java file: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private String[] optimizePrompt() {
        String[] prompt = {
                "Review Java code for areas that can be improved in terms of best practices, " +
                "correctness, and efficiency. Provide feedback on how to make the code better: ",

                "Errors found in the code by the java compiler:",

                "Check the code's provided dependencies list for any known vulnerabilities " +
                "or compatibility issues, and provide recommendations for how to address them:"};
        return prompt;
    }

    private String getErrorMessages(boolean success, DiagnosticCollector<JavaFileObject> diagnostics) {
        if (success) {
            return "No errors found";
        } else {
            StringBuilder errorBuilder = new StringBuilder();
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                errorBuilder.append(diagnostic.getMessage(null)).append("\n");
            }
            String errorMessage = "Compilation Error: " + errorBuilder;
            logger.log(Level.SEVERE, errorMessage);
            return errorMessage;
        }
    }
}