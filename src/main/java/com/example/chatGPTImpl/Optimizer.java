package com.example.chatGPTImpl;

import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import javax.net.ssl.HttpsURLConnection;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.ToolProvider;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Optimizer {
    private String apiEndpoint;
    private String apiKey;
    private String model;
    private String host;
    private int port;
    private String username;
    private String password;
    private String transportProtocol;
    private final Logger LOGGER = Logger.getLogger(Optimizer.class.getName());
    private final int MAX_TOKENS = 2048;
    private final double TEMPERATURE = 0.5;

    public Optimizer(String apiEndpoint, String apiKey, String model, String host,
                     int port, String username, String password, String transportProtocol) {
        this.apiEndpoint = apiEndpoint;
        this.apiKey = apiKey;
        this.model = model;
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.transportProtocol = transportProtocol;
    }

    public void optimizeCodeAndEmail(String repositoryUrl, String recipientEmail) {
        File repositoryDirectory = null;
        try {
            repositoryDirectory = downloadRepository(repositoryUrl);
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
                solution = apiRequest(javaFile.getAbsolutePath(), optimizePrompt(), errors);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            sendOptimizerEmail(recipientEmail, errors, solution, javaFile.getName());
        }
    }

    public File downloadRepository(String repositoryUrl) throws Exception {
        String[] parts = repositoryUrl.split("/");
        if (parts.length < 4 || !parts[0].equals("https:") || !parts[2].endsWith(".com")) {
            throw new IllegalArgumentException("Invalid repository URL: " + repositoryUrl);
        }
        String service = parts[2].replaceAll("\\.com", "");
        String owner = parts[3];
        String repoName = parts[4].replaceAll("\\.git", "").replaceAll("#.*", "");

        String downloadUrl = String.format("https://%s.com/%s/%s/archive/refs/heads/main.zip", service, owner, repoName);
        URL url = new URL(downloadUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try (InputStream inputStream = connection.getInputStream()) {
            File zipFile = File.createTempFile("repo", ".zip");
            zipFile.deleteOnExit();
            try (FileOutputStream outputStream = new FileOutputStream(zipFile)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }
            return extractZipFile(zipFile);
        }
    }

    private File downloadZipFile(String downloadUrl) throws Exception {
        Path zipFilePath = Path.of(System.getProperty("java.io.tmpdir")).resolve(UUID.randomUUID().toString() + ".zip");
        try (InputStream inputStream = new URL(downloadUrl).openStream();
             FileOutputStream outputStream = new FileOutputStream(zipFilePath.toFile())) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }
        return zipFilePath.toFile();
    }

    private File extractZipFile(File zipFile) throws Exception {
        String repoName = zipFile.getName().substring(0, zipFile.getName().indexOf(".zip"));
        Path localDirectory = Files.createTempDirectory(repoName);
        try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(zipFile.toPath()))) {
            ZipEntry zipEntry = zipInputStream.getNextEntry();
            while (zipEntry != null) {
                Path path = localDirectory.resolve(zipEntry.getName());
                if (zipEntry.isDirectory()) {
                    Files.createDirectories(path);
                } else {
                    Files.copy(zipInputStream, path);
                }
                zipEntry = zipInputStream.getNextEntry();
            }
        }
        Files.delete(zipFile.toPath());

        return localDirectory.toFile();
    }

    private void sendOptimizerEmail(String recipientEmail, String error, String solution, String fileName) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(host);
        mailSender.setPort(port);
        mailSender.setUsername(username);
        mailSender.setPassword(password);
        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", transportProtocol);
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");

        SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setFrom(username);
        mailMessage.setTo(username);
        mailMessage.setSubject("Suggested Reviewed Output for class: " + fileName);
        mailMessage.setText("Errors In Code: " + error);
        mailMessage.setText("Optimized Solution: " + solution);
        mailSender.send(mailMessage);
        LOGGER.log(Level.INFO, "Suggested output sent successfully to email address: " + recipientEmail);
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

    private String optimizePrompt() {
        return "Improve code, avoid crashes, explain changes, generate optimized code only if necessary.";
    }

    private JSONObject createRequestData(String prompt) {
        JSONObject data = new JSONObject();
        data.put("model", model);
        data.put("prompt", prompt);
        data.put("max_tokens", MAX_TOKENS);
        data.put("temperature", TEMPERATURE);
        return data;
    }

    private String apiRequest(String filePath, String prompt, String errors) throws IOException {
        HttpsURLConnection connection = null;
        try {
            URL url = new URL(apiEndpoint);
            connection = (HttpsURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setDoOutput(true);

            String code = Files.readString(Paths.get(filePath));
            JSONObject requestData = createRequestData(prompt + ": \n" + code + "\n" + errors);

            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(requestData.toString().getBytes());
            }

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP error code: " + responseCode);
            }

            try (InputStream inputStream = connection.getInputStream()) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                StringBuilder responseBuilder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    responseBuilder.append(line);
                }
                String response = responseBuilder.toString();
                JSONObject json = new JSONObject(response);
                return json.getJSONArray("choices").getJSONObject(0).getString("text");
            } catch (JSONException e) {
                throw new IOException("Error parsing JSON response", e);
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String[] getErrorMessages(boolean success, DiagnosticCollector<JavaFileObject> diagnostics) {
        if (success) {
            return new String[]{"No errors found", ""};
        } else {
            StringBuilder errorBuilder = new StringBuilder();
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                errorBuilder.append(diagnostic.getMessage(null)).append("\n");
            }
            String errorMessage = "Compilation Error: " + errorBuilder.toString();
            LOGGER.log(Level.SEVERE, errorMessage);
            return new String[]{errorMessage, ""};
        }
    }
}