package com.animateai.backend.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VideoGenerationServiceTest {

    @TempDir
    Path directory;

    private Process fakeProcess(int exitCode) throws InterruptedException {
        Process process = mock(Process.class);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream("FFmpeg diagnostics".getBytes()));
        when(process.waitFor()).thenReturn(exitCode);
        return process;
    }

    @Test
    void generatesUniqueVideosWithExpectedCommand() throws Exception {
        Path outputDirectory = directory.resolve("generated-videos");
        Process process = fakeProcess(0);
        VideoGenerationService service = new VideoGenerationService(outputDirectory, builder -> {
            List<String> command = builder.command();
            assertEquals(List.of("ffmpeg", "-nostdin", "-hide_banner", "-loglevel", "error", "-n",
                    "-f", "lavfi", "-i", "color=c=blue:s=1280x720:d=5", "-c:v", "libx264",
                    "-pix_fmt", "yuv420p"), command.subList(0, command.size() - 1));
            assertTrue(builder.redirectErrorStream());
            assertTrue(Files.isDirectory(outputDirectory));
            Files.write(Path.of(command.get(command.size() - 1)), new byte[] {1});
            return process;
        });

        var first = service.generateTestVideo();
        var second = service.generateTestVideo();

        assertEquals("SUCCESS", first.status());
        assertTrue(first.fileName().matches("test-[0-9a-f-]{36}\\.mp4"));
        assertEquals("generated-videos/" + first.fileName(), first.path());
        assertNotEquals(first.fileName(), second.fileName());
        verify(process, times(2)).waitFor();
    }

    @Test
    void passesSpecialCharactersThroughUtf8FileAndCleansUp() throws Exception {
        Process process = fakeProcess(0);
        String title = "It's: C:\\tmp 100% %{x} é";
        Path outputDirectory = directory.resolve("generated-videos");
        VideoGenerationService service = new VideoGenerationService(outputDirectory, builder -> {
            List<String> command = builder.command();
            assertEquals(outputDirectory.toAbsolutePath().toFile(), builder.directory());
            assertTrue(command.contains("color=c=0x151923:s=1280x720:d=5"));
            String filter = command.get(command.indexOf("-vf") + 1);
            assertTrue(filter.startsWith("drawtext=textfile=title-"));
            assertTrue(filter.contains(":expansion=none:fontcolor=white:fontsize=40"));
            assertTrue(filter.contains(":x=(w-text_w)/2:y=(h-text_h)/2"));
            assertFalse(String.join(" ", command).contains(title));
            String textName = filter.substring("drawtext=textfile=".length(), filter.indexOf(':'));
            assertEquals(title, Files.readString(outputDirectory.resolve(textName)));
            assertEquals(List.of("-c:v", "libx264", "-pix_fmt", "yuv420p"),
                    command.subList(command.size() - 5, command.size() - 1));
            Files.write(Path.of(command.get(command.size() - 1)), new byte[] {1});
            return process;
        });
        var first = service.generateTextVideo(title);
        var second = service.generateTextVideo(title);
        assertEquals("SUCCESS", first.status());
        assertTrue(first.fileName().matches("text-[0-9a-f-]{36}\\.mp4"));
        assertEquals("generated-videos/" + first.fileName(), first.path());
        assertNotEquals(first.fileName(), second.fileName());
        try (var files = Files.list(outputDirectory)) {
            assertTrue(files.allMatch(file -> file.toString().endsWith(".mp4")));
        }
    }

    @Test
    void wrapsLongTitlesIncludingWordsWithoutSpaces() throws Exception {
        Process process = fakeProcess(0);
        VideoGenerationService service = new VideoGenerationService(directory, builder -> {
            String filter = builder.command().get(builder.command().indexOf("-vf") + 1);
            String textName = filter.substring("drawtext=textfile=".length(), filter.indexOf(':'));
            String wrapped = Files.readString(directory.resolve(textName));
            assertEquals("W".repeat(200), wrapped.replace("\n", ""));
            assertTrue(wrapped.lines().allMatch(line -> line.length() <= 28));
            Files.write(Path.of(builder.command().get(builder.command().size() - 1)), new byte[] {1});
            return process;
        });
        service.generateTextVideo("W".repeat(200));
    }

    @Test
    void cleansUpTextFilesWhenFfmpegFailsOrCannotStart() throws Exception {
        Process process = fakeProcess(1);
        VideoGenerationService failedRender = new VideoGenerationService(directory, builder -> process);
        assertThrows(VideoGenerationException.class, () -> failedRender.generateTextVideo("Title"));
        VideoGenerationService failedStart = new VideoGenerationService(directory, builder -> {
            throw new IOException("FFmpeg not installed");
        });
        assertThrows(VideoGenerationException.class, () -> failedStart.generateTextVideo("Title"));
        try (var files = Files.list(directory)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void rejectsNonzeroExitEvenIfFileExists() throws Exception {
        Process process = fakeProcess(1);
        VideoGenerationService service = new VideoGenerationService(directory, builder -> {
            Files.write(Path.of(builder.command().get(builder.command().size() - 1)), new byte[] {1});
            return process;
        });
        assertThrows(VideoGenerationException.class, service::generateTestVideo);
    }

    @Test
    void rejectsMissingOutputFile() throws Exception {
        Process process = fakeProcess(0);
        VideoGenerationService service = new VideoGenerationService(directory, builder -> process);
        assertThrows(VideoGenerationException.class, service::generateTestVideo);
    }

    @Test
    void rejectsEmptyOutputFile() throws Exception {
        Process process = fakeProcess(0);
        VideoGenerationService service = new VideoGenerationService(directory, builder -> {
            Files.createFile(Path.of(builder.command().get(builder.command().size() - 1)));
            return process;
        });
        assertThrows(VideoGenerationException.class, service::generateTestVideo);
    }

    @Test
    void hidesProcessStartupErrorDetails() {
        VideoGenerationService service = new VideoGenerationService(directory, builder -> {
            throw new IOException("private system details");
        });
        var error = assertThrows(VideoGenerationException.class, service::generateTestVideo);
        assertFalse(error.getMessage().contains("private system details"));
    }

    @Test
    void preservesInterruptAndStopsProcess() throws Exception {
        Process process = fakeProcess(0);
        when(process.waitFor()).thenThrow(new InterruptedException());
        when(process.isAlive()).thenReturn(true);
        VideoGenerationService service = new VideoGenerationService(directory, builder -> process);
        try {
            assertThrows(VideoGenerationException.class, service::generateTestVideo);
            assertTrue(Thread.currentThread().isInterrupted());
            verify(process).destroyForcibly();
        } finally {
            Thread.interrupted();
        }
    }
}
