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
    private String apiEndpoint;
    private String apiKey;
    private Logger logger;
    private final String PROPERTIESPATH = "/Users/brian/Code/java/chatGPTImpl/src/main/resources/application.properties";

    public ApiHttpsEmailClient(String apiEndpoint, String apiKey) {
        this.apiEndpoint = apiEndpoint;
        this.apiKey = apiKey;
    }

    public void getLogger(Logger logger) {
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

    public void sendEmail(String recipientEmail, String solution, String fileName, String username, String password) {
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

    public String apiRequest(String[] prompts, String model) throws IOException {
        JSONObject requestData = createRequestData(String.join("", prompts), model);
        String response = sendHttpsRequest(new URL(apiEndpoint), requestData.toString(), apiKey);
        JSONObject json = new JSONObject(response);
        return json.getJSONArray("choices").getJSONObject(0).getString("text");
    }

    private String sendHttpsRequest(URL url, String requestBody, String apiKey) throws IOException {
        HttpsURLConnection connection = null;
        try {
            connection = (HttpsURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setDoOutput(true);

            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(requestBody.getBytes());
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
                return responseBuilder.toString();
            } catch (JSONException e) {
                throw new IOException("Error parsing JSON response", e);
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private JSONObject createRequestData(String prompt, String model) {
        JSONObject data = new JSONObject();
        data.put("model", model);
        data.put("prompt", prompt);
        data.put("max_tokens", MAX_TOKENS);
        data.put("temperature", TEMPERATURE);
        System.out.println("__________________________ Start of Request Data __________________________");
        System.out.println(data);
        System.out.println("__________________________ End of Request Data __________________________");
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
}