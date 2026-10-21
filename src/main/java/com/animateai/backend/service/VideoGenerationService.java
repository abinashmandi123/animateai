package com.animateai.backend.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

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
        return generateVideo("test", null);
    }

    public VideoGenerationResult generateTextVideo(String text) {
        return generateVideo("text", text);
    }

    private VideoGenerationResult generateVideo(String prefix, String text) {
        String fileName = prefix + "-" + UUID.randomUUID() + ".mp4";
        Path outputFile = outputDirectory.resolve(fileName);
        Path textFile = null;
        Path fontFile = null;
        Process process = null;
        try {
            Files.createDirectories(outputDirectory);
            List<String> command = new ArrayList<>(List.of(
                    "ffmpeg", "-nostdin", "-hide_banner", "-loglevel", "error", "-n",
                    "-f", "lavfi", "-i", text == null
                            ? "color=c=blue:s=1280x720:d=5" : "color=c=0x151923:s=1280x720:d=5"));
            if (text != null) {
                textFile = Files.createTempFile(outputDirectory, "title-", ".txt");
                Files.writeString(textFile, wrapTitle(text), StandardCharsets.UTF_8);
                String filter = "drawtext=textfile=" + textFile.getFileName()
                        + ":expansion=none:fontcolor=white:fontsize=40:line_spacing=12"
                        + ":x=(w-text_w)/2:y=(h-text_h)/2";

                // A local font filename avoids Windows drive-letter escaping in the filter.
                String windowsDirectory = System.getenv("SystemRoot");
                if (windowsDirectory != null) {
                    Path arial = Path.of(windowsDirectory, "Fonts", "arial.ttf");
                    if (Files.isRegularFile(arial)) {
                        fontFile = Files.createTempFile(outputDirectory, "font-", ".ttf");
                        Files.copy(arial, fontFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        filter += ":fontfile=" + fontFile.getFileName();
                    }
                }
                command.addAll(List.of("-vf", filter));
            }
            command.addAll(List.of("-c:v", "libx264", "-pix_fmt", "yuv420p",
                    text == null ? outputFile.toString() : outputFile.toAbsolutePath().toString()));
            ProcessBuilder builder = new ProcessBuilder(command);
            if (text != null) {
                // Only generated basenames occur in the filter, never user text or absolute paths.
                builder.directory(outputDirectory.toAbsolutePath().toFile());
            }
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
            deleteTemporaryFile(textFile);
            deleteTemporaryFile(fontFile);
        }
    }

    // Short lines keep even a 200-character title readable within the frame.
    private String wrapTitle(String text) {
        String remaining = text.strip().replaceAll("\\s+", " ");
        List<String> lines = new ArrayList<>();
        while (remaining.codePointCount(0, remaining.length()) > 28) {
            int end = remaining.offsetByCodePoints(0, 28);
            int space = remaining.lastIndexOf(' ', end);
            if (space > 0) {
                end = space;
            }
            lines.add(remaining.substring(0, end));
            remaining = remaining.substring(end).stripLeading();
        }
        lines.add(remaining);
        return String.join("\n", lines);
    }

    private void deleteTemporaryFile(Path file) {
        if (file != null) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException ex) {
                log.warn("Unable to remove temporary rendering file", ex);
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
