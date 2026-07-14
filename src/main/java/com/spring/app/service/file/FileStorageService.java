package com.spring.app.service.file;

import com.spring.app.common.FileInfoProperties;
import com.spring.app.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class FileStorageService {

    private final FileInfoProperties fileInfoProperties;

    public String storeImage(MultipartFile file, String oldFileName) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("File not found");
        }

        // 10MB = 10 * 1024 * 1024 = 10485760 bytes
        long maxFileSize = 10 * 1024 * 1024; // 10MB
        if (file.getSize() > maxFileSize) {
            throw new BusinessException("File too large");
        }

        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new BusinessException("Invalid file name");
        }

        String extension = originalFileName.substring(originalFileName.lastIndexOf('.') + 1);
        String newFileName = UUID.randomUUID() + "." + extension;

        File targetDir = new File(fileInfoProperties.getServerPath());
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw new BusinessException("Failed to upload");
        }

        if (oldFileName != null && !oldFileName.isBlank()) {
            File oldFile = new File(targetDir, oldFileName);
            if (oldFile.exists() && !oldFile.delete()) {
                throw new BusinessException("Failed to delete");
            }
        }

        File newFile = new File(targetDir, newFileName);
        Files.copy(file.getInputStream(), newFile.toPath());

        return newFileName;
    }

    public void deleteFile(String fileName) {
        try {
            if (fileName == null || fileName.isBlank()) {
                throw new BusinessException("File not found");
            }

            File file = new File(fileInfoProperties.getServerPath(), fileName);
            if (!file.exists() || !file.delete()) {
                throw new BusinessException("Failed to delete");
            }
        } catch (BusinessException e) {
            log.error(String.valueOf(e));
        }
    }

}
