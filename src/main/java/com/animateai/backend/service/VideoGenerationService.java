package com.animateai.backend.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class VideoGenerationService {

    private static final Logger log = LoggerFactory.getLogger(VideoGenerationService.class);
    private final Path outputDirectory;
    private final ProcessStarter processStarter;

    public VideoGenerationService() {
        this(Path.of("generated-videos"), ProcessBuilder::start);
    }

    // Allows tests to use a temporary directory and a fake FFmpeg process.
    VideoGenerationService(Path outputDirectory, ProcessStarter processStarter) {
        this.outputDirectory = outputDirectory;
        this.processStarter = processStarter;
    }

    public VideoGenerationResult generateTestVideo() {
        String fileName = "test-" + UUID.randomUUID() + ".mp4";
        Path outputFile = outputDirectory.resolve(fileName);
        Process process = null;
        try {
            Files.createDirectories(outputDirectory);
            ProcessBuilder builder = new ProcessBuilder(
                    "ffmpeg", "-nostdin", "-hide_banner", "-loglevel", "error", "-n",
                    "-f", "lavfi", "-i", "color=c=blue:s=1280x720:d=5",
                    "-c:v", "libx264", "-pix_fmt", "yuv420p", outputFile.toString());
            builder.redirectErrorStream(true);
            process = processStarter.start(builder);

            // Drain merged stdout/stderr before waiting so FFmpeg cannot block on a full pipe.
            String output;
            try (var stream = process.getInputStream()) {
                output = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.error("FFmpeg failed with exit code {}. Output: {}", exitCode, output);
                throw new VideoGenerationException();
            }
            if (!Files.isRegularFile(outputFile) || Files.size(outputFile) == 0) {
                log.error("FFmpeg finished without a nonempty video file. Output: {}", output);
                throw new VideoGenerationException();
            }
            return new VideoGenerationResult("SUCCESS", fileName, "generated-videos/" + fileName);
        } catch (IOException ex) {
            log.error("Unable to execute FFmpeg or access the generated video", ex);
            throw new VideoGenerationException();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("Video generation was interrupted", ex);
            throw new VideoGenerationException();
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    @FunctionalInterface
    interface ProcessStarter {
        Process start(ProcessBuilder builder) throws IOException;
    }

    public record VideoGenerationResult(String status, String fileName, String path) {
    }
}
