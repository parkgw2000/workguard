package com.example.workguard.service;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;
import java.io.*;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class OcrService {

    @Value("${clova.ocr.url}")
    private String CLOVA_OCR_URL;

    @Value("${clova.ocr.secret}")
    private String CLOVA_SECRET;

    public String sendToClovaOcr(MultipartFile file) throws IOException {
        String boundary = "----" + UUID.randomUUID().toString();
        HttpURLConnection connection = (HttpURLConnection) new URL(CLOVA_OCR_URL).openConnection();
        connection.setUseCaches(false);
        connection.setDoOutput(true);
        connection.setDoInput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("X-OCR-SECRET", CLOVA_SECRET);
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        try (DataOutputStream request = new DataOutputStream(connection.getOutputStream())) {
            // JSON 메시지
            String message = "{"
                    + "\"version\": \"V2\","
                    + "\"requestId\": \"" + UUID.randomUUID() + "\","
                    + "\"timestamp\": " + System.currentTimeMillis() + ","
                    + "\"images\": [{\"format\": \"png\", \"name\": \"image\"}]"
                    + "}";
            writeFormField(request, boundary, "message", "application/json", message);
            writeFileField(request, boundary, "file", file.getOriginalFilename(), file.getBytes());
            request.writeBytes("--" + boundary + "--\r\n");
            request.flush();
        }

        // 응답 처리
        String jsonResponse;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            jsonResponse = reader.lines().collect(Collectors.joining());
        } catch (IOException e) {
            throw new RuntimeException("Failed to get OCR result: " + e.getMessage());
        }

        // 라인 추출 및 그룹핑 → JSON 반환
        String extractedText = extractTextFromJson(jsonResponse);
        return groupLinesToJson(extractedText).toString();
    }

    private void writeFormField(DataOutputStream out, String boundary, String fieldName, String contentType, String value) throws IOException {
        out.writeBytes("--" + boundary + "\r\n");
        out.writeBytes("Content-Disposition: form-data; name=\"" + fieldName + "\"\r\n");
        out.writeBytes("Content-Type: " + contentType + "\r\n\r\n");
        out.writeBytes(value + "\r\n");
    }

    private void writeFileField(DataOutputStream out, String boundary, String fieldName, String fileName, byte[] fileData) throws IOException {
        out.writeBytes("--" + boundary + "\r\n");
        out.writeBytes("Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + fileName + "\"\r\n");
        out.writeBytes("Content-Type: application/octet-stream\r\n\r\n");
        out.write(fileData);
        out.writeBytes("\r\n");
    }

    // OCR 응답 → 줄별 텍스트 추출
    private String extractTextFromJson(String jsonResponse) {
        JSONObject response = new JSONObject(jsonResponse);
        JSONArray images = response.optJSONArray("images");
        if (images == null) return "";

        TreeMap<Integer, List<Word>> lines = new TreeMap<>();
        for (int i = 0; i < images.length(); i++) {
            JSONObject image = images.getJSONObject(i);
            JSONArray fields = image.optJSONArray("fields");
            if (fields == null) continue;

            for (int j = 0; j < fields.length(); j++) {
                JSONObject field = fields.getJSONObject(j);
                String text = field.optString("inferText", "");
                if (text.isEmpty()) continue;

                JSONArray vertices = field.getJSONObject("boundingPoly").getJSONArray("vertices");
                int y = vertices.getJSONObject(0).getInt("y");
                int x = vertices.getJSONObject(0).getInt("x");
                int lineKey = findClosestLineKey(lines.keySet(), y);
                if (lineKey == -1) {
                    lineKey = y;
                    lines.put(lineKey, new ArrayList<>());
                }
                lines.get(lineKey).add(new Word(text, x));
            }
        }

        // y 기준 정렬, 각 줄 x 기준 정렬
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, List<Word>> entry : lines.entrySet()) {
            List<Word> words = entry.getValue();
            words.sort(Comparator.comparingInt(w -> w.x));
            for (Word w : words) sb.append(w.text).append(" ");
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    private int findClosestLineKey(Set<Integer> keys, int y) {
        for (int key : keys) {
            if (Math.abs(key - y) <= 10) return key;
        }
        return -1;
    }

    private static class Word {
        String text;
        int x;

        Word(String text, int x) {
            this.text = text;
            this.x = x;
        }
    }

    // ✅ 줄 그룹핑 후 JSON 배열로 변환
    private JSONArray groupLinesToJson(String fullText) {
        List<String> lines = Arrays.stream(fullText.split("\n"))
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .collect(Collectors.toList());

        JSONArray grouped = new JSONArray();
        List<String> currentGroup = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String prefix = line.length() >= 2 ? line.substring(0, 2) : "";

            if (prefix.matches(".*\\d.*")) {
                // 이전 그룹 저장
                if (!currentGroup.isEmpty()) {
                    grouped.put(new JSONArray(currentGroup));
                    currentGroup.clear();
                }
                currentGroup.add(line);
            } else if (!currentGroup.isEmpty()) {
                currentGroup.add(line);
            }
        }

        if (!currentGroup.isEmpty()) {
            grouped.put(new JSONArray(currentGroup));
        }

        return grouped;
    }
}
