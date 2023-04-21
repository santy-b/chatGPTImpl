package com.example.chatGPTImpl;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.ToolProvider;
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
            String errors = getErrorMessages(success, diagnostics)[0];
            String solution = null;
            try {
                solution = client.apiRequest(javaFile.getAbsolutePath(), optimizePrompt(), model, errors);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
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

    private String optimizePrompt() {
        return "Optimize the following code and suggest performance improvements " +
                "in code. If the response exceeds the maximum token limit, keep the" +
                "response concise and only return suggestions for affected methods in code.";
    }

    private String[] getErrorMessages(boolean success, DiagnosticCollector<JavaFileObject> diagnostics) {
        if (success) {
            return new String[]{"No errors found", ""};
        } else {
            StringBuilder errorBuilder = new StringBuilder();
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                errorBuilder.append(diagnostic.getMessage(null)).append("\n");
            }
            String errorMessage = "Compilation Error: " + errorBuilder;
            logger.log(Level.SEVERE, errorMessage);
            return new String[]{errorMessage, ""};
        }
    }
}