package com.example.chatGPTImpl;

import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ApiHttpsEmailClient {
    private final String HOST = "smtp.gmail.com";
    private final int PORT = 587;
    private final int MAX_TOKENS = 2048;
    private final double TEMPERATURE = 0.2;
    private final String PROPERTIESPATH = "/Users/brian/Code/java/chatGPTImpl/src/main/resources/application.properties";
    private Logger logger;
    public ApiHttpsEmailClient(Logger logger) {
        this.logger = logger;
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

    public void sendEmail(String recipientEmail, String solution,
                          String fileName, String username, String password) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(HOST);
        mailSender.setPort(PORT);
        mailSender.setUsername(username);
        mailSender.setPassword(password);
        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");

        SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setFrom(username);
        mailMessage.setTo(username);
        mailMessage.setSubject("Suggested Improvements For: " + fileName);
        mailMessage.setText(solution);
        mailSender.send(mailMessage);
        logger.log(Level.INFO, "Suggested output sent successfully to email address: " + recipientEmail);
    }

    public String apiRequest(String filePath, String prompt, String model, String errors) throws IOException {
        String apiEndpoint = getProperty("api.endpoint");
        String apiKey = getProperty("api.key");

        List<String> codeChunks = getCodeChunks(filePath, prompt);
        List<String> responses = new ArrayList<>();
        for (String chunk : codeChunks) {
            HttpsURLConnection connection = null;
            try {
                URL url = new URL(apiEndpoint);
                connection = (HttpsURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Authorization", "Bearer " + apiKey);
                connection.setDoOutput(true);

                JSONObject requestData = createRequestData(prompt + ": \n" + chunk + "\n" + errors, model);

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
                    responses.add(json.getJSONArray("choices").getJSONObject(0).getString("text"));
                } catch (JSONException e) {
                    throw new IOException("Error parsing JSON response", e);
                }
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
        return String.join("", responses);
    }

    private List<String> getCodeChunks(String filePath, String prompt) throws IOException {
        String code = Files.readString(Paths.get(filePath));
        List<String> codeChunks = new ArrayList<>();
        int chunkSize = MAX_TOKENS - prompt.length() - 3;
        int startIndex = 0;
        int endIndex = chunkSize;
        while (startIndex < code.length()) {
            if (endIndex >= code.length()) {
                endIndex = code.length();
            } else {
                while (!Character.isWhitespace(code.charAt(endIndex))) {
                    endIndex--;
                }
            }
            codeChunks.add(code.substring(startIndex, endIndex));
            startIndex = endIndex;
            endIndex = startIndex + chunkSize;
        }
        return codeChunks;
    }

    private JSONObject createRequestData(String prompt, String model) {
        JSONObject data = new JSONObject();
        data.put("model", model);
        data.put("prompt", prompt);
        data.put("max_tokens", MAX_TOKENS);
        data.put("temperature", TEMPERATURE);
        return data;
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

    private String getProperty(String property) throws IOException {
        Properties props = new Properties();
        Reader reader = new FileReader(PROPERTIESPATH);
        props.load(reader);
        return props.getProperty(property);
    }
}