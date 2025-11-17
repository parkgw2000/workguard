package com.example.workguard.Service;

import com.example.workguard.Client.PythonModelClient;
import com.example.workguard.Service.ChatGptService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class OcrService {

    @Value("${clova.ocr.url}")
    private String CLOVA_OCR_URL;

    @Value("${clova.ocr.secret}")
    private String CLOVA_SECRET;

    private final PythonModelClient pythonModelClient;
    private final ChatGptService chatGptService;

    public OcrService(PythonModelClient pythonModelClient, ChatGptService chatGptService) {
        this.pythonModelClient = pythonModelClient;
        this.chatGptService = chatGptService;
    }

    public String sendToClovaOcr(MultipartFile file) throws IOException {
        String boundary = "----" + UUID.randomUUID();
        HttpURLConnection connection = (HttpURLConnection) new URL(CLOVA_OCR_URL).openConnection();
        connection.setUseCaches(false);
        connection.setDoOutput(true);
        connection.setDoInput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("X-OCR-SECRET", CLOVA_SECRET);
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        try (DataOutputStream request = new DataOutputStream(connection.getOutputStream())) {
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

        String jsonResponse;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            jsonResponse = reader.lines().collect(Collectors.joining());
        } catch (IOException e) {
            throw new RuntimeException("OCR 결과를 받는 중 오류 발생: " + e.getMessage());
        }

        // OCR 결과 텍스트 줄 단위로 추출
        String extractedText = extractTextFromJson(jsonResponse);
        // 줄 그룹핑 후 JSON 배열 반환
        JSONArray groupedLinesJson = groupLinesToJson(extractedText);

        System.out.println("jsontest" + groupedLinesJson);

        return groupedLinesJson.toString();
    }
    public String processOcrAndPythonSummaries(JSONArray groupedLinesJson) {

        List<JSONArray> groups = new ArrayList<>();
        for (int i = 0; i < groupedLinesJson.length(); i++) {
            groups.add(groupedLinesJson.getJSONArray(i));
        }

        // 각 그룹을 List<String>으로 변환 후 파이썬 호출
        List<Map<String, Object>> pythonResponses = new ArrayList<>();
        for (JSONArray group : groups) {
            List<String> lines = new ArrayList<>();
            for (int j = 0; j < group.length(); j++) {
                lines.add(group.getString(j));
            }
            Map<String, Object> response = pythonModelClient.sendLinesToPythonModel(lines);
            pythonResponses.add(response);
        }
        List<String> summaries = pythonResponses.stream()
                .map(resp -> (String) resp.get("summary"))
                .filter(s -> s != null && !s.isEmpty())
                .collect(Collectors.toList());

        String chatgptEvaluation = chatGptService.summarizeSummaries(summaries);

        // 예시로 파이썬 응답을 JSON 문자열로 합쳐서 반환
        JSONObject result = new JSONObject();
        result.put("python_responses", pythonResponses);
        result.put("chatgpt_evaluation", chatgptEvaluation);
        return result.toString();
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

                System.out.println("Extracted word: " + text);  // 텍스트 추출 로그

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

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, List<Word>> entry : lines.entrySet()) {
            List<Word> words = entry.getValue();
            words.sort(Comparator.comparingInt(w -> w.x));
            for (Word w : words) sb.append(w.text).append(" ");
            sb.append("\n");
        }
        String result = sb.toString().trim();

        System.out.println("Extracted full text:\n" + result);  // 최종 텍스트 출력

        return result;
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

    private JSONArray groupLinesToJson(String fullText) {
        List<String> lines = Arrays.stream(fullText.split("\n"))
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .collect(Collectors.toList());

        JSONArray grouped = new JSONArray();
        List<String> currentGroup = new ArrayList<>();

        for (String line : lines) {
            // 3글자 이상인지 확인 후 3글자만 추출
            String prefix = line.length() >= 3 ? line.substring(0, 3) : "";

            boolean isGroupStart = false;
            if (prefix.length() == 3) {
                // 첫글자부터 숫자인지 확인
                char firstChar = prefix.charAt(0);
                char secondChar = prefix.charAt(1);
                char thirdChar = prefix.charAt(2);

                // "숫자 + 숫자 + '.'" 또는 "숫자 + '.' + 기타" 형태인지 체크
                if (Character.isDigit(firstChar) && Character.isDigit(secondChar) && thirdChar == '.') {
                    isGroupStart = true; // ex) "12."
                } else if (Character.isDigit(firstChar) && secondChar == '.' ) {
                    isGroupStart = true; // ex) "1.x" (여기서 x는 '.'가 아니면 false)
                } else if (Character.isDigit(firstChar) && secondChar == '.' && thirdChar == '.') {
                    // 이건 불가능한 경우지만 혹시 몰라서...
                    isGroupStart = false;
                }
            }

            if (isGroupStart) {
                if (!currentGroup.isEmpty()) {
                    grouped.put(new JSONArray(currentGroup));
                    System.out.println("그룹 추가됨: " + currentGroup);
                    currentGroup.clear();
                }
                currentGroup.add(line);
            } else if (!currentGroup.isEmpty()) {
                currentGroup.add(line);
            }
        }

        if (!currentGroup.isEmpty()) {
            grouped.put(new JSONArray(currentGroup));
            System.out.println("마지막 그룹 추가됨: " + currentGroup);
        }

        return grouped;
    }
}
