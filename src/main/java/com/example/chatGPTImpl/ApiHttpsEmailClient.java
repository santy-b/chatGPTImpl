package com.example.chatGPTImpl;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ApiHttpsEmailClient {
    private final int MAX_TOKENS = 2048;
    private final double TEMPERATURE = 0.2;
    private String apiEndpoint;
    private String apiKey;
    private String username;
    private String password;
    private String token;
    private Logger logger;

    public ApiHttpsEmailClient(String apiEndpoint, String apiKey, String username, String password, String token) {
        this.apiEndpoint = apiEndpoint;
        this.apiKey = apiKey;
        this.username = username;
        this.password = password;
        this.token = token;
    }

    public void getLogger(Logger logger) {
        this.logger = logger;
    }

    public List<String> getFilesFromGitHubAPI(String owner, String repo, String branch) throws Exception {
        String url = "https://api.github.com/repos/" + owner + "/" + repo + "/contents?ref=" + branch;
        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();

        con.setRequestMethod("GET");
        con.setRequestProperty("User-Agent", "Mozilla/5.0");
        con.setRequestProperty("Authorization", "Bearer " + token);

        int responseCode = con.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                response.append(line);
            }
            in.close();

            JSONArray jsonArray = new JSONArray(response.toString());
            List<String> fileContents = new ArrayList<>();

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject json = jsonArray.getJSONObject(i);
                String fileName = json.getString("name");

                if (json.has("path")) {
                    String fileContent = getFileContentFromGitHubAPI(owner, repo, branch, json.getString("path"));
                    fileContents.add(fileContent);

                    Thread.sleep(100);
                } else {
                    System.out.println("Skipping file " + fileName + " due to missing 'path' field in the response.");
                }
            }
            return fileContents;
        } else {
            throw new Exception("Failed to get files from GitHub API. Response code: " + responseCode);
        }
    }

    private String getFileContentFromGitHubAPI(String owner, String repo, String branch, String filePath) throws Exception {
        String rawFileUrl = "https://raw.githubusercontent.com/" + owner + "/" + repo + "/" + branch + "/" + filePath;
        URL obj = new URL(rawFileUrl);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();
        con.setRequestMethod("GET");
        con.setRequestProperty("User-Agent", "Mozilla/5.0");

        int responseCode = con.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                response.append(line);
            }
            in.close();

            return response.toString();
        } else {
            throw new Exception("Failed to get file content from GitHub API. Response code: " + responseCode);
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

    public String sendApiRequest(String prompt, String model) throws IOException {
        JSONObject requestData = new JSONObject();
        requestData.put("model", model);
        requestData.put("prompt", prompt);
        requestData.put("max_tokens", MAX_TOKENS);
        requestData.put("temperature", TEMPERATURE);
        System.out.println("__________________________ START OF REQUEST DATA __________________________");
        System.out.println("Prompt:\n" + requestData.getString("prompt").replaceAll(":", ":\n"));
        System.out.println("___________________________ END OF REQUEST DATA ___________________________");

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
}