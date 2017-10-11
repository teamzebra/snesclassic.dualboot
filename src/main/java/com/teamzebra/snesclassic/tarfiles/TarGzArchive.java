package com.teamzebra.snesclassic.tarfiles;

import org.apache.tools.tar.TarEntry;
import org.apache.tools.tar.TarInputStream;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * Borrowed from here:
 * http://www.deroneriksson.com/tutorials/java/files/extract-tar-gz-file-in-java
 */
public class TarGzArchive {

    private final Map<String, byte[]> content;

    public TarGzArchive(final File file) throws IOException {
        content = new HashMap<String, byte[]>();

        try {
            final FileInputStream fis = new FileInputStream(file);
            final GZIPInputStream gis = new GZIPInputStream(fis);
            final TarInputStream tis = new TarInputStream(gis);

            TarEntry entry;
            ByteArrayOutputStream baos;

            while ((entry = tis.getNextEntry()) != null) {
                final String entryName = entry.getName();

                if (entryName != null) {
                    baos = new ByteArrayOutputStream();
                    tis.copyEntryContents(baos);
                    final byte[] bytes = baos.toByteArray();
                    content.put(entryName, bytes);
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public byte[] getEntryContent(final String entryName) {
        return content.get(entryName);
    }

    public Set<String> getEntries() {
        return content.keySet();
    }
}
