package com.ibrasoft.lensbridge.service.board.offline;

import com.ibrasoft.lensbridge.dto.upload.response.ErrorResponse;
import com.ibrasoft.lensbridge.exception.ApiResponseException;
import com.ibrasoft.lensbridge.model.board.Device;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * A USB kit (contract C4): one zip whose root holds a single {@code MusallahBoard/} folder,
 * ready to unzip onto a stick:
 * <pre>
 * MusallahBoard/setup-&lt;id8&gt;.mbu                    per board, only with includeSetup
 * MusallahBoard/content-&lt;id8&gt;.mbu                  per board
 * MusallahBoard/musallahboard-app-&lt;v&gt;.mbu           latest board app, when GitHub answers
 * MusallahBoard/musallahboard-agent-&lt;v&gt;-arm64.mbu   latest agent, when GitHub answers
 * </pre>
 * Every board reads every file and keeps only what is its own and newer, so one stick serves
 * any number of boards. The zip is written to a temporary file first: memory stays bounded by
 * one board's package, and every failure (a poster image that cannot be fetched, say) is still
 * a JSON error rather than a truncated download.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UsbKitService {

    static final String FOLDER = "MusallahBoard/";

    private final OfflineBundleService offlineBundleService;
    private final SetupFileService setupFileService;
    private final ReleaseChannelCache releases;
    private final ContentSigningService signingService;

    /**
     * A built kit on disk. The caller streams it and must {@link #close} it.
     *
     * @param omitted what the kit goes without, and why (empty when complete)
     */
    public record Kit(Path file, String filename, List<String> omitted) implements AutoCloseable {
        @Override
        public void close() {
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                log.warn("Could not delete USB kit temp file {}: {}", file, e.getMessage());
            }
        }
    }

    /**
     * @param online setup files include the backend URL and a new claim secret (with includeSetup)
     */
    public Kit build(List<Device> devices, String filename, int days, boolean includeSetup, boolean online) {
        if (days < 1 || days > OfflineBundleService.MAX_DAYS) {
            throw new ApiResponseException(HttpStatus.BAD_REQUEST,
                    ErrorResponse.of("days must be between 1 and " + OfflineBundleService.MAX_DAYS));
        }
        if (devices.isEmpty()) {
            throw new ApiResponseException(HttpStatus.NOT_FOUND, ErrorResponse.of("There are no boards to make a kit for"));
        }
        signingService.requireConfigured();

        Path file;
        try {
            file = Files.createTempFile("musallahboard-usb-kit-", ".zip");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<String> omitted = new ArrayList<>();
        try (OutputStream out = Files.newOutputStream(file); ZipOutputStream zip = new ZipOutputStream(out)) {
            ZipEntry folder = new ZipEntry(FOLDER);
            zip.putNextEntry(folder);
            zip.closeEntry();
            for (Device device : devices) {
                String shortId = device.getId().toString().substring(0, 8);
                // Content first: it is what can fail (posters), and a failure then leaves no
                // setup file issued, with its rotated claim secret, for a kit nobody receives.
                byte[] content = bytesOf(offlineBundleService.build(device.getId(), days));
                if (includeSetup) {
                    put(zip, "setup-" + shortId + ".mbu", bytesOf(setupFileService.build(device.getId(), online)));
                }
                put(zip, "content-" + shortId + ".mbu", content);
            }
            for (ReleaseChannelCache.Kind kind : ReleaseChannelCache.Kind.values()) {
                ReleaseChannelCache.Lookup lookup = releases.latest(kind);
                if (lookup.pkg() != null) {
                    put(zip, lookup.pkg().filename(), lookup.pkg().bytes());
                } else {
                    omitted.add(kind.label + ": " + lookup.problem());
                }
            }
        } catch (IOException e) {
            deleteQuietly(file);
            throw new UncheckedIOException(e);
        } catch (RuntimeException e) {
            deleteQuietly(file);
            throw e;
        }
        log.info("Built USB kit {} for {} board(s): {} days, setup files {}{}", filename, devices.size(), days,
                includeSetup ? (online ? "online" : "offline") : "no",
                omitted.isEmpty() ? "" : ", without " + String.join("; ", omitted));
        return new Kit(file, filename, List.copyOf(omitted));
    }

    /** Packages are zips already; stored, not deflated again. */
    private static void put(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        ZipEntry entry = new ZipEntry(FOLDER + name);
        CRC32 crc = new CRC32();
        crc.update(bytes);
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(bytes.length);
        entry.setCompressedSize(bytes.length);
        entry.setCrc(crc.getValue());
        zip.putNextEntry(entry);
        zip.write(bytes);
        zip.closeEntry();
    }

    private static byte[] bytesOf(OfflineBundle bundle) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bundle.writeTo(out);
        return out.toByteArray();
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // best effort
        }
    }
}
