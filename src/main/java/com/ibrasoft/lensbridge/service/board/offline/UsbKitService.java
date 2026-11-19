package com.ibrasoft.lensbridge.service.board.offline;

import com.ibrasoft.lensbridge.dto.upload.response.ErrorResponse;
import com.ibrasoft.lensbridge.exception.ApiResponseException;
import com.ibrasoft.lensbridge.model.board.Device;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * A USB kit (contract C4): one zip whose root holds a single {@code MusallahBoard/} folder,
 * ready to unzip onto a stick:
 * <pre>
 * MusallahBoard/content-&lt;id8&gt;.mbu                  per board
 * MusallahBoard/musallahboard-app-&lt;v&gt;.mbu           latest board app, when GitHub answers
 * MusallahBoard/musallahboard-agent-&lt;v&gt;-arm64.mbu   latest agent, when GitHub answers
 * MusallahBoard/setup-&lt;id8&gt;.mbu                    per board, only with includeSetup
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
    private final PlatformTransactionManager transactionManager;

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
            // Everything slow or fallible first, outside any transaction and with nothing
            // stored: content (posters are fetched) and the releases (GitHub).
            for (Device device : devices) {
                put(zip, "content-" + shortId(device) + ".mbu", bytesOf(offlineBundleService.build(device.getId(), days)));
            }
            for (ReleaseChannelCache.Kind kind : ReleaseChannelCache.Kind.values()) {
                ReleaseChannelCache.Lookup lookup = releases.latest(kind);
                if (lookup.pkg() != null) {
                    put(zip, lookup.pkg().filename(), lookup.pkg().bytes());
                } else {
                    omitted.add(kind.label + ": " + lookup.problem());
                }
            }
            if (includeSetup) {
                // Then every board's setup file in one transaction that commits only once the
                // zip is complete: a failure anywhere (a board revoked meanwhile, a full disk)
                // rolls back every sequence and claim secret, so the files boards already
                // have keep working. Boards take setup files before content from one stick,
                // so their place at the end of the zip does not matter. In id order, so two
                // kits at once lock the same rows in the same order (no deadlock on Postgres).
                List<Device> byId = devices.stream().sorted(Comparator.comparing(Device::getId)).toList();
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    try {
                        for (Device device : byId) {
                            put(zip, "setup-" + shortId(device) + ".mbu",
                                    bytesOf(setupFileService.build(device.getId(), online)));
                        }
                        zip.close();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
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

    private static String shortId(Device device) {
        return device.getId().toString().substring(0, 8);
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
