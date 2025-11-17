package com.example.workguard.Controller;

import com.example.workguard.Service.OcrService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/ocr")
public class OcrController {

    private final OcrService ocrService;

    public OcrController(OcrService ocrService) {
        this.ocrService = ocrService;
    }

    @PostMapping("/upload")
    public ResponseEntity<String> uploadImage(@RequestParam("file") MultipartFile file) {
        try {
            String jsonGroupedText = ocrService.sendToClovaOcr(file);
            return ResponseEntity.ok(jsonGroupedText);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/gptchk")
    public ResponseEntity<String> processGroupedLines(@RequestBody String groupedJson) {
        try {
            // RequestBody로 들어온 JSON String → JSONArray 변환
            JSONArray groupedLinesJson = new JSONArray(groupedJson);

            // service 실행
            String resultJson = ocrService.processOcrAndPythonSummaries(groupedLinesJson);

            return ResponseEntity.ok(resultJson);

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Error: " + e.getMessage());
        }
    }
}
