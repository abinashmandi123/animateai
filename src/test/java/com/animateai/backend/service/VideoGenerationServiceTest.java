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
