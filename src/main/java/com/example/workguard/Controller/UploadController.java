package com.example.workguard.Controller;

import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.example.workguard.Dto.UserDTO;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.example.workguard.Repository.StorageRepository;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.example.workguard.Entity.Storage;
import com.example.workguard.Entity.Users;
import java.time.LocalDateTime;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class UploadController {

    private final AmazonS3Client amazonS3Client;
    private final StorageRepository storageRepository;
    @Value("${cloud.aws.s3.bucket}")
    private String bucket;
    @Value("${cloud.aws.region.static}")
    private String region;


    //파일 업로드용
    @PostMapping("/file")
    public ResponseEntity<String> uploadFile(@RequestParam("file") MultipartFile file, HttpSession session) {
        try {
            UserDTO userDTO = (UserDTO) session.getAttribute("user");
            if (userDTO == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User not logged in");
            }

            Long usernum = userDTO.getUsernum();
            String folderPath = usernum + "/";

            ListObjectsV2Result result = amazonS3Client.listObjectsV2(bucket, folderPath);
            int fileCount = result.getKeyCount() + 1;

            String extension = getExtension(file.getOriginalFilename());
            String fileName = usernum + "_" + fileCount + extension;
            String s3Key = folderPath + fileName;
            String fileUrl = "https://" + bucket + "/" + s3Key;

            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(file.getContentType());
            metadata.setContentLength(file.getSize());

            amazonS3Client.putObject(bucket, s3Key, file.getInputStream(), metadata);
            //PutObjectRequest request = new PutObjectRequest(bucket, s3Key, file.getInputStream(), metadata)
            //        .withCannedAcl(CannedAccessControlList.PublicRead);

            //amazonS3Client.putObject(request);
            // ✅ Storage 엔티티 저장
            Storage storage = new Storage();
            Users user = new Users(); // ID만 설정해도 @ManyToOne 매핑 가능
            user.setUsernum(usernum);

            storage.setUser(user);
            storage.setOriginalName(file.getOriginalFilename());
            storage.setFileUrl(fileUrl);
            storage.setS3Key(s3Key);
            storage.setFileSize(file.getSize());
            storage.setContentType(file.getContentType());
            storage.setVersion(1);
            storage.setIsLatest(true);
            storage.setUploadedAt(LocalDateTime.now());

            storageRepository.save(storage);

            return ResponseEntity.ok(fileUrl);

        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("File upload failed");
        }
    }

    //파일리스트 확인용
    @GetMapping("/list")
    public ResponseEntity<?> listUserFiles(HttpSession session) {
        UserDTO userDTO = (UserDTO) session.getAttribute("user");
        if (userDTO == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User not logged in");
        }

        Long usernum = userDTO.getUsernum();
        String prefix = usernum + "/"; // S3에서 유저 전용 폴더

        ListObjectsV2Result result = amazonS3Client.listObjectsV2(bucket, prefix);
        List<Map<String, Object>> files = result.getObjectSummaries().stream().map(obj -> {
            Map<String, Object> fileInfo = new HashMap<>();
            fileInfo.put("fileName", obj.getKey().replace(prefix, "")); // 폴더 경로 제거
            fileInfo.put("size", obj.getSize());
            fileInfo.put("lastModified", obj.getLastModified());
            fileInfo.put("url", "https://" + bucket + "/" + obj.getKey()); // 파일 접근 URL
            fileInfo.put("img", "https://" + bucket + ".s3." + region + ".amazonaws.com/" +obj.getKey()); // 파일 미리보기 url
            return fileInfo;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(files);
    }
    
    //파일삭제용
    @DeleteMapping("/file/{storageId}")
    public ResponseEntity<String> deleteFile(@PathVariable Long storageId, HttpSession session) {
        UserDTO userDTO = (UserDTO) session.getAttribute("user");
        if (userDTO == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User not logged in");
        }

        Optional<Storage> optionalStorage = storageRepository.findById(storageId);
        if (optionalStorage.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File not found");
        }

        Storage storage = optionalStorage.get();

        // 권한 확인: 본인 파일만 삭제
        if (!storage.getUser().getUsernum().equals(userDTO.getUsernum())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("No permission to delete this file");
        }

        // S3에서 삭제
        amazonS3Client.deleteObject(bucket, storage.getS3Key());

        // DB에서 삭제
        storageRepository.delete(storage);

        return ResponseEntity.ok("File deleted successfully");
    }

    //파일 다운로드용
    @GetMapping("/files")
    public ResponseEntity<List<Map<String, String>>> listUserStoredFiles(HttpSession session) {
        UserDTO userDTO = (UserDTO) session.getAttribute("user");
        if (userDTO == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<Storage> files = storageRepository.findByUser_Usernum(userDTO.getUsernum());

        List<Map<String, String>> fileList = files.stream().map(file -> {
            Map<String, String> map = new HashMap<>();
            map.put("fileName", file.getOriginalName());
            map.put("url", file.getFileUrl());
            map.put("storageId", String.valueOf(file.getStorageId()));
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(fileList);
    }

    private String getExtension(String fileName) {
        if (fileName != null && fileName.contains(".")) {
            return fileName.substring(fileName.lastIndexOf("."));
        } else {
            return "";
        }
    }

}
