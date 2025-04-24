package com.ibrasoft.lensbridge.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.Transformation;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Map;

@Service
public class CloudinaryService {

    private final Cloudinary cloudinary;

    public CloudinaryService(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    public String uploadImage(byte[] fileBytes, String fileName) throws IOException {
        Map uploadResult = cloudinary.uploader().upload(fileBytes, ObjectUtils.asMap(
                "folder", "lensbridge",
                "public_id", fileName
        ));

        return (String) uploadResult.get("secure_url");

    }

    public String uploadVideo(File transcodedFile, String fileName) throws IOException {
        Map<?, ?> uploadResult = cloudinary.uploader().upload(transcodedFile, ObjectUtils.asMap(
                "folder", "lensbridge",
                "resource_type", "video",
                "public_id", fileName
        ));
        return (String) uploadResult.get("secure_url");
    }



}
