package com.example.chatGPTImpl;

import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.ToolProvider;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Optimizer {
    private class NullResolver implements EntityResolver {
        @Override
        public InputSource resolveEntity(String publicId, String systemId) {
            return new InputSource(new StringReader(""));
        }
    }
    private String model;
    private Logger logger = Logger.getLogger(Optimizer.class.getName());
    @Autowired
    private ApiHttpsEmailClient client = new AppConfig().apiHttpsEmailClient();
    public Optimizer(String model) {
        this.model = model;
    }

    public void optimizeCodeAndEmail(String owner, String repo, String branch, String recipientEmail) throws Exception {
        client.getLogger(logger);
        List<String> fileContents = client.getFilesFromGitHubAPI(owner, repo, branch);

        for (String fileContent : fileContents) {
            File tempFile = File.createTempFile("temp", ".java");
            FileUtils.writeStringToFile(tempFile, fileContent, StandardCharsets.UTF_8);

            if (tempFile.getName().endsWith(".java")) {
                DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
                JavaCompiler.CompilationTask task = compileJavaFile(tempFile, diagnostics);
                boolean success = task.call();
                String errors = getErrorMessages(success, diagnostics);
                File pomFile = findPomFile(tempFile.getName(), tempFile.getParentFile());
                String prompt = (optimizePrompt()[0] + "\n" + fileToString(tempFile) + "\n" + optimizePrompt()[1] + "\n" + errors + "\n" + optimizePrompt()[2] + "\n" + getPomDependencies(pomFile)).replaceAll("\\s{2,}", " ");
                String solution = null;
                String className = extractClassName(fileContent);

                try {
                    solution = client.sendApiRequest(prompt, model);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                client.sendEmail(recipientEmail, solution, className);

                tempFile.delete();
            }
        }
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
            documentBuilderFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
            documentBuilder.setEntityResolver(new NullResolver());
            try (InputStream is = new FileInputStream(pomFile)) {
                Document document = documentBuilder.parse(is);
                dependencies.addAll(extractDependencies(document));
                String javaVersion = document.getElementsByTagName("java.version").item(0).getTextContent();
                if (javaVersion != null && !javaVersion.isEmpty()) {
                    dependencies.add("java.version:" + javaVersion);
                }
            }
        } catch (ParserConfigurationException | SAXException | IOException e) {
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

    private String fileToString(File file) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line = reader.readLine();
            while (line != null) {
                stringBuilder.append(line).append("\n");
                line = reader.readLine();
            }
        }
        return stringBuilder.toString();
    }

    private String[] optimizePrompt() {
        String[] prompt = {
               "Review Java code for areas that can be improved in terms of best practices, correctness, and efficiency. Provide feedback in code format on how to make the code better.\n" +
               "Structure the response explaining What needs to be fixed and below that the proposed solution in code format. If no adjustment's are necessary simply say \"This method is already optimized\": ",
               "Errors found in the code by the java compiler: ",
               "Check the code's provided dependencies list for any known vulnerabilities or compatibility issues, and provide recommendations for how to address them: "};
        return prompt;
    }

    private String extractClassName(String fileContent) {
        int classIndex = fileContent.indexOf("class");
        if (classIndex != -1) {
            int spaceIndex = fileContent.indexOf(" ", classIndex + 1);
            if (spaceIndex != -1) {
                int braceIndex = fileContent.indexOf("{", spaceIndex + 1);
                if (braceIndex != -1) {
                    return fileContent.substring(spaceIndex + 1, braceIndex).trim() + ".java";
                }
            }
        }
        return "UnknownClassName";
    }

    private List<String> extractDependencies(Document document) {
        NodeList dependencyList = document.getElementsByTagName("dependency");
        return IntStream.range(0, dependencyList.getLength())
                .mapToObj(dependencyList::item)
                .map(dependency -> {
                    NodeList groupId = ((Element) dependency).getElementsByTagName("groupId");
                    NodeList artifactId = ((Element) dependency).getElementsByTagName("artifactId");
                    NodeList version = ((Element) dependency).getElementsByTagName("version");
                    return new String[]{
                            groupId.item(0) != null ? groupId.item(0).getTextContent() : "",
                            artifactId.item(0) != null ? artifactId.item(0).getTextContent() : "",
                            version.item(0) != null ? version.item(0).getTextContent() : ""
                    };
                })
                .filter(parts -> !Arrays.stream(parts).anyMatch(String::isEmpty))
                .map(parts -> String.join(":", parts))
                .collect(Collectors.toList());
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