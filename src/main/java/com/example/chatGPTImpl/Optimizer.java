package com.example.chatGPTImpl;

import org.json.JSONException;
import org.json.JSONObject;
import javax.net.ssl.HttpsURLConnection;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.ToolProvider;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;


public class Optimizer {
    private String apiEndpoint;
    private String apiKey;
    private String model;
    private final Logger LOGGER = Logger.getLogger(Optimizer.class.getName());
    private final int MAX_TOKENS = 4000;
    private final double TEMPERATURE = 1.0;

    public Optimizer(String apiEndpoint, String apiKey, String model) {
        this.apiEndpoint = apiEndpoint;
        this.apiKey = apiKey;
        this.model = model;
    }

    public String[] processCode(String filePath, String promptMessage) throws Exception {
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaCompiler.CompilationTask task = compileJavaFile(filePath, diagnostics);
        boolean success = task.call();
        String errors = getErrorMessages(success, diagnostics)[0];
        String result = apiRequestWithPrompt(filePath, promptMessage, errors);
        String[] output = {errors, result};
        return output;
    }

    public String optimizePrompt() {
        return "Optimize & simplify this code follow OOP, provide a brief explanation";
    }

    public String errorPrompt() {
        return "Fix this code, provide a brief explanation";
    }

    public String toString(String[] stringArray) {
        return Arrays.toString(stringArray);
    }

    private JSONObject createRequestData(String prompt) {
        JSONObject data = new JSONObject();
        data.put("model", model);
        data.put("prompt", prompt);
        data.put("max_tokens", MAX_TOKENS);
        data.put("temperature", TEMPERATURE);
        return data;
    }

    private JavaCompiler.CompilationTask compileJavaFile(String filePath, DiagnosticCollector<JavaFileObject> diagnostics) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null);
        Iterable<? extends JavaFileObject> fileObjects = fileManager.getJavaFileObjects(Paths.get(filePath));
        return compiler.getTask(null, fileManager, diagnostics, null, null, fileObjects);
    }

    private String apiRequest(JSONObject requestData) throws IOException {
        HttpsURLConnection connection = (HttpsURLConnection) new URL(apiEndpoint).openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        connection.setDoOutput(true);
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
    }

    private String apiRequestWithPrompt(String filePath, String prompt, String errors) throws IOException {
        String code = Files.readString(Paths.get(filePath));
        JSONObject requestData = createRequestData(prompt + ": \n" + code + "\n" + errors);
        return apiRequest(requestData);
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