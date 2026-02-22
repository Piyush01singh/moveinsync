package com.moveinsync.mdm.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BackupImportRequest {
    @NotBlank
    private String filePath;

    private boolean replaceExisting = true;
}
