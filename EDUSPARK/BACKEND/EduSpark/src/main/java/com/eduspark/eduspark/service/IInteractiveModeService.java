package com.eduspark.eduspark.service;

import com.eduspark.eduspark.dto.interactive.InteractiveModeResult;
import com.eduspark.eduspark.pojo.entity.ChatSession;
import org.springframework.web.multipart.MultipartFile;

public interface IInteractiveModeService {

    InteractiveModeResult handleMessage(ChatSession session, String userMessage, MultipartFile[] attachments);
}
