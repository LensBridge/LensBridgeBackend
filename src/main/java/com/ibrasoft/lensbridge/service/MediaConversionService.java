package com.ibrasoft.lensbridge.service;

import com.ibrasoft.lensbridge.exception.VideoProcessingException;
import com.ibrasoft.lensbridge.exception.VideoTooLongException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;

@Service
public class MediaConversionService {

    @Value("${uploads.video.maxduration}")
    private double MAX_DURATION_SECONDS;

    public File convertHEICToJPEG(InputStream inputStream, String fileName) throws IOException, InterruptedException {
        File inputFile = File.createTempFile("input-" + fileName, ".heic");
        File outputFile = File.createTempFile("output-" + fileName, ".jpg");

        // Save stream to file
        try (FileOutputStream fos = new FileOutputStream(inputFile)) {
            inputStream.transferTo(fos);
        }

        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg",
                "-i", inputFile.getAbsolutePath(),
                "-q:v", "2",
                "-y",
                outputFile.getAbsolutePath()
        );

        Process process = pb.inheritIO().start();
        int exitCode = process.waitFor();

        inputFile.delete();

        if (exitCode != 0) {
            outputFile.delete();
            throw new VideoProcessingException("FFmpeg failed with exit code " + exitCode);
        }
        return outputFile;
    }

    public File transcodeToHevc(InputStream inputStream, String fileName) throws IOException, InterruptedException {
        File inputFile = File.createTempFile("input-", ".mp4");
        File outputFile = File.createTempFile("output-", ".mp4");

        // Save stream to file
        try (FileOutputStream fos = new FileOutputStream(inputFile)) {
            inputStream.transferTo(fos);
        }

        // Enforce max duration
        double duration = getVideoDurationInSeconds(inputFile);
        if (duration > MAX_DURATION_SECONDS) {
            inputFile.delete();
            outputFile.delete();
            throw new VideoTooLongException("Video is too long! Maximum allowed is " + MAX_DURATION_SECONDS + " seconds.");
        }

        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg",
                "-hwaccel", "cuda",                     // optional: GPU-assisted decoding
                "-i", inputFile.getAbsolutePath(),
                "-c:v", "hevc_nvenc",                   // GPU-based HEVC encoder (NVENC)
                "-preset", "p5",                        // p1 = fastest, p7 = slowest (best quality)
                "-cq", "28",                            // Constant quality (lower = better)
                "-c:a", "aac", "-b:a", "128k",
                "-y",
                outputFile.getAbsolutePath()
        );

        Process process = pb.inheritIO().start();
        int exitCode = process.waitFor();

        inputFile.delete();

        if (exitCode != 0) {
            outputFile.delete();
            throw new VideoProcessingException("FFmpeg failed with exit code " + exitCode);
        }

        return outputFile;
    }

    private double getVideoDurationInSeconds(File file) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(
                "ffprobe", "-v", "error",
                "-select_streams", "v:0",
                "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1",
                file.getAbsolutePath()
        );

        Process process = pb.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line = reader.readLine();
        int exitCode = process.waitFor();

        if (exitCode != 0 || line == null) {
            throw new VideoProcessingException("Failed to get video duration");
        }

        return Double.parseDouble(line.trim());
    }
}
