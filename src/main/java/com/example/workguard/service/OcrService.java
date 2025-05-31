package com.example.workguard.service;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.DataOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import java.util.stream.Collectors;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.TreeMap;
import java.util.ArrayList;
import java.util.Comparator;

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

            // 1. JSON message part
            String message = "{"
                    + "\"version\": \"V2\","
                    + "\"requestId\": \"" + UUID.randomUUID() + "\","
                    + "\"timestamp\": " + System.currentTimeMillis() + ","
                    + "\"images\": ["
                    + "    {"
                    + "        \"format\": \"png\","
                    + "        \"name\": \"image\""
                    + "    }"
                    + "]"
                    + "}";
            writeFormField(request, boundary, "message", "application/json", message);

            // 2. Image file part
            writeFileField(request, boundary, "file", file.getOriginalFilename(), file.getBytes());

            // 3. End of multipart
            request.writeBytes("--" + boundary + "--\r\n");
            request.flush();
        }

        // Read response
        String jsonResponse;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            jsonResponse = reader.lines().collect(Collectors.joining());
        } catch (IOException e) {
            throw new RuntimeException("Failed to get OCR result: " + e.getMessage());
        }

        // JSON 응답에서 텍스트만 추출
        return extractTextFromJson(jsonResponse);
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

    private String extractTextFromJson(String jsonResponse) {
        JSONObject response = new JSONObject(jsonResponse);
        JSONArray images = response.optJSONArray("images");
        if (images == null) {
            return "";
        }

        // 라인별 단어 모으기 위한 Map (y 좌표 -> List<word>)
        TreeMap<Integer, List<Word>> lines = new TreeMap<>();

        for (int i = 0; i < images.length(); i++) {
            JSONObject image = images.getJSONObject(i);
            JSONArray fields = image.optJSONArray("fields");
            if (fields == null) continue;

            for (int j = 0; j < fields.length(); j++) {
                JSONObject field = fields.getJSONObject(j);
                String text = field.optString("inferText", "");
                if (text.isEmpty()) continue;

                // boundingPoly 중 첫번째 vertex y 좌표 추출
                JSONArray vertices = field.getJSONObject("boundingPoly").getJSONArray("vertices");
                int y = vertices.getJSONObject(0).getInt("y");

                // y 좌표 근처로 그룹핑 (오차범위 10 정도)
                int lineKey = findClosestLineKey(lines.keySet(), y);

                if (lineKey == -1) {
                    lineKey = y;
                    lines.put(lineKey, new ArrayList<>());
                }

                // x 좌표도 필요 - 첫번째 vertex의 x
                int x = vertices.getJSONObject(0).getInt("x");

                lines.get(lineKey).add(new Word(text, x));
            }
        }

        // 결과 문자열 생성
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, List<Word>> entry : lines.entrySet()) {
            List<Word> words = entry.getValue();
            // x 좌표로 정렬
            words.sort(Comparator.comparingInt(w -> w.x));
            for (Word w : words) {
                sb.append(w.text).append(" ");
            }
            sb.append("\n");
        }

        return sb.toString().trim();
    }

    // y 좌표 근접 라인 찾기
    private int findClosestLineKey(Set<Integer> keys, int y) {
        for (int key : keys) {
            if (Math.abs(key - y) <= 10) { // 오차범위 10 픽셀 이내면 같은 라인으로 판단
                return key;
            }
        }
        return -1;
    }

    // 단어와 x 좌표를 저장하는 클래스
    private static class Word {
        String text;
        int x;

        Word(String text, int x) {
            this.text = text;
            this.x = x;
        }
    }
}