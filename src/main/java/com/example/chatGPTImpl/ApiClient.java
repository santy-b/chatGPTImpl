package com.example.chatGPTImpl;

import com.grpc.ApiRequest;
import com.grpc.ApiServiceImpl;
import org.json.JSONObject;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ApiClient {
    private final int MAX_TOKENS = 2048;
    private final double TEMPERATURE = 0.2;
    private String apiEndpoint;
    private String apiKey;
    private String username;
    private String password;
    private Logger logger;

    public ApiClient(String apiEndpoint, String apiKey, String username, String password) {
        this.apiEndpoint = apiEndpoint;
        this.apiKey = apiKey;
        this.username = username;
        this.password = password;
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

    public void sendEmail(String recipientEmail, String solution, String fileName) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost("smtp.gmail.com");
        mailSender.setPort(587);
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

    public String sendApiRequest(String prompt, String model) throws IOException, InterruptedException, ExecutionException {
        JSONObject requestData = new JSONObject();
        requestData.put("model", model);
        requestData.put("prompt", prompt);
        requestData.put("max_tokens", MAX_TOKENS);
        requestData.put("temperature", TEMPERATURE);
        System.out.println("__________________________ START OF REQUEST DATA __________________________");
        System.out.println("Prompt:\n" + requestData.getString("prompt").replaceAll(":", ":\n"));
        System.out.println("___________________________ END OF REQUEST DATA ___________________________");

        return grpcRequest(requestData);
    }

    private String grpcRequest(JSONObject requestData) throws InterruptedException, ExecutionException {
        ApiRequest apiRequest = ApiRequest.newBuilder()
                .setUrl(apiEndpoint)
                .setApiKey(apiKey)
                .setRequestBody(requestData.toString())
                .build();
        ApiServiceImpl apiService = new ApiServiceImpl();

        // Create an ExecutorService to execute the request
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        final String[] responseDataString = new String[1];

        // Submit the request to the ExecutorService
        Future<?> future = executorService.submit(() -> {
            CountDownLatch latch = new CountDownLatch(1);
            apiService.sendRequest(null, apiRequest, apiResponse -> {
                byte[] responseData = apiResponse.getData().toByteArray();
                JSONObject json = new JSONObject(new String(responseData));
                String result = json.getJSONArray("choices").getJSONObject(0).getString("text");
                responseDataString[0] = result;
                latch.countDown();
            });
            try {
                latch.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        // Wait for the request to complete
        future.get();

        // Shut down the ExecutorService
        executorService.shutdown();

        return responseDataString[0];
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