package com.example.workguard.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

@Service
@RequiredArgsConstructor
public class OcrService {

    @Value("${clova.ocr.invoke-url}")
    private String invokeUrl;

    @Value("${clova.ocr.secret-key}")
    private String secretKey;

    public String extractText(MultipartFile multipartFile) throws IOException {
        File file = convertToFile(multipartFile);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.set("X-OCR-SECRET", secretKey);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new FileSystemResource(file));

            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
            RestTemplate restTemplate = new RestTemplate();

            ResponseEntity<String> response = restTemplate.postForEntity(invokeUrl, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                return response.getBody();
            } else {
                throw new IOException("Clova OCR 응답 실패 - 상태코드: " + response.getStatusCode());
            }
        } finally {
            file.delete(); // 임시 파일 정리
        }
    }

    private File convertToFile(MultipartFile multipartFile) throws IOException {
        File file = File.createTempFile("upload-", multipartFile.getOriginalFilename());
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(multipartFile.getBytes());
        }
        return file;
    }
}