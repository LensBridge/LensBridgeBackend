package com.ibrasoft.lensbridge.service.board.offline;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * A fully built, signed content package ({@code .mbu}), held in memory until it is written.
 * The name predates the package format: the same package now also serves online sync.
 * <p>
 * Everything that can fail — assembly, image downloads, serialization — has already happened
 * by the time one of these exists, so {@link #writeTo} can only fail on the transport. That
 * is what lets the endpoint still return a proper 4xx/5xx instead of a truncated zip.
 */
public record OfflineBundle(String filename, List<Entry> entries) {

    /**
     * @param stored true for content that is already compressed (images), which gains
     *               nothing from deflate. JSON entries ({@code mbu.json}, {@code mbu.sig},
     *               payloads) are deflated.
     */
    public record Entry(String name, byte[] content, boolean stored) {}

    /** Writes the zip. Does not close {@code out}; the servlet container owns it. */
    public void writeTo(OutputStream out) throws IOException {
        ZipOutputStream zip = new ZipOutputStream(out);
        for (Entry entry : entries) {
            ZipEntry zipEntry = new ZipEntry(entry.name());
            if (entry.stored()) {
                CRC32 crc = new CRC32();
                crc.update(entry.content());
                zipEntry.setMethod(ZipEntry.STORED);
                zipEntry.setSize(entry.content().length);
                zipEntry.setCompressedSize(entry.content().length);
                zipEntry.setCrc(crc.getValue());
            }
            zip.putNextEntry(zipEntry);
            zip.write(entry.content());
            zip.closeEntry();
        }
        zip.finish();
        zip.flush();
    }
}
