package com.teamzebra.snesclassic;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.teamzebra.snesclassic.tarfiles.TarGzArchive;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author TeamZebra
 */
public class Main {

    /**
     * The directory the dump file needs to be located in.
     */
    private static final String DUMP_FOLDER = "dump";

    /**
     * The directory the HMOD will be output to.
     */
    private static final String HMOD_FOLDER = "nesc_hybrid_system.hmod";

    /**
     * The list of valid NESC dump files.
     */
    private static final String NES_102_DUMP = "dp-nes-release-v1.0.2-0-g99e37e1.tar.gz";
    private static final String NES_103_DUMP = "dp-nes-release-v1.0.3-0-gc4c703b.tar.gz";
    private static final String HVC_105_DUMP = "dp-hvc-release-v1.0.5-0-g2f04d11.tar.gz";

    private static final List<String> VALID_NESC_DUMP_FILES = ImmutableList.of(
            NES_102_DUMP, NES_103_DUMP, HVC_105_DUMP);

    /**
     * The dump file that the user actually supplied.
     */
    private static String chosenDumpFile;

    /**
     * Main program driver.
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        System.out.println("SNES / NES Classic Hybrid Dual Boot Tool v0.2");
        System.out.println("By TeamZebra: https://github.com/teamzebra/snesclassic.dualboot");

        // verify that the bundled files for the resulting HMOD weren't deleted
        System.out.println("Verifying that the pre-bundled HMOD files are present...");
        verifyBundledHmodFiles();
        System.out.println("Pre-bundled HMOD files verified successfully!");

        // verify that the bundled files for the NESC launcher weren't deleted
        System.out.println("Verifying that the pre-bundled NESC launcher files are present...");
        verifyBundledNescLauncherFiles();
        System.out.println("Pre-bundled NESC launcher files verified successfully!");

        // detect which dump file we need to extract
        System.out.println("Detecting NESC dump archive file...");
        detectNESCDump();
        System.out.println(String.format("Detected a NESC dump archive successfully! File: %s", chosenDumpFile));

        // extract the NESC dump archive
        System.out.println("Extracting the NESC dump archive...");
        extractNescTarGzArchive();
        System.out.println("NESC dump archive extracted successfully!");

        // copy over the files from the NESC dump that we need
        System.out.println("Copying the NESC dump files to the HMOD folder...");
        copyDumpFilesToHmod();
        System.out.println("NESC dump files copied successfully!");

        // do text patches
        System.out.println("Patching up text scripts in the HMOD folder...");
        patchTextScriptsInHmod();
        System.out.println("Patched text scripts successfully!");

        // do game patches
        System.out.println("Patching up game files in the HMOD folder...");
        patchGameFilesInHmod();
        System.out.println("Patched game files successfully!");

        // do binary patches
        System.out.println("Patching up binary files in the HMOD folder...");
        patchBinaryFilesInHmod();
        System.out.println("Patched binary files successfully!");

        System.out.println("Complete! Install the resulting HMOD using hakchi2, and copy the CLV-S-00NES");
        System.out.println("folder to the games_snes folder in hakchi to sync it to your console.");
    }

    /**
     * Verifies that all the non-copyrighted files that we shipped with the application are
     * still there and weren't deleted or anything.
     */
    private static void verifyBundledHmodFiles() {
        verifyHmodEntityExists("install");
        verifyHmodEntityExists("uninstall");
        verifyHmodEntityExists("bin/switch_to_nes");
        verifyHmodEntityExists("bin/switch_to_snes");
        verifyHmodEntityExists("bin/switch_to_nes_child");
        verifyHmodEntityExists("etc/nesgames/CLV-P-0SNES/CLV-P-0SNES.desktop");
        verifyHmodEntityExists("etc/nesgames/CLV-P-0SNES/CLV-P-0SNES.png");
        verifyHmodEntityExists("etc/nesgames/CLV-P-0SNES/CLV-P-0SNES_small.png");
    }

    /**
     * Quick verification that a file/directory exists in the HMOD folder, so we can
     * know the user didn't delete anything we shipped the application with.
     * @param path The path of the entity to verify
     */
    private static void verifyHmodEntityExists(final String path) {
        if (!(new File(String.format("%s/%s", HMOD_FOLDER, path)).exists())) {
            throw new RuntimeException(String.format(
                    "'%s' not found within the HMOD directory, please redownload the application", path));
        }
    }

    /**
     * Verifies that the NES launcher script is present.
     */
    private static void verifyBundledNescLauncherFiles() {
        final String[] files = new String[] {
                "CLV-S-00NES/CLV-S-00NES.desktop",
                "CLV-S-00NES/CLV-S-00NES.png",
                "CLV-S-00NES/CLV-S-00NES_small.png"
        };

        for (String f : files) {
            if (!(new File(f).exists())) {
                throw new RuntimeException(String.format(
                        "'%s' not found, please redownload the application", f));
            }
        }
    }

    /**
     * Determines which of the valid NESC dump files (if any) the user has.
     */
    private static void detectNESCDump() {
        for (String file : VALID_NESC_DUMP_FILES) {
            if ((new File(String.format("%s/%s", DUMP_FOLDER, file))).exists()) {
                chosenDumpFile = file;
                break;
            }
        }

        if (chosenDumpFile == null) {
            throw new RuntimeException("No valid NESC dump files found in the dump folder");
        }
    }

    /**
     * Extract the user's chosen NESC dump .tar.gz archive to its current directory.
     * @throws IOException
     */
    public static void extractNescTarGzArchive()
            throws IOException {
        final File file = new File(String.format("%s/%s", DUMP_FOLDER, chosenDumpFile));

        if (!file.exists()) {
            throw new FileNotFoundException(String.format("Couldn't find TAR archive %s", file.getCanonicalPath()));
        }

        final TarGzArchive archive = new TarGzArchive(file);
        final Set<String> entries = archive.getEntries();

        for (String entry : entries) {
            final String outPath = String.format("%s/%s", DUMP_FOLDER, entry);
            final File newFile = new File(outPath);

            System.out.println(String.format("Extracting archive file: ./%s", outPath));

            if (entry.endsWith("/")) {
                newFile.mkdirs();
                continue;
            }

            final File parent = newFile.getParentFile();

            if ((parent != null) && (!parent.exists())) {
                parent.mkdirs();
            }

            final FileOutputStream fos = new FileOutputStream(newFile);
            final byte[] bytes = archive.getEntryContent(entry);

            fos.write(bytes);
            fos.close();
        }
    }

    /**
     * Copy all the necessary files from the NESC dump to the HMOD folder.
     * @throws IOException
     */
    private static void copyDumpFilesToHmod()
            throws IOException {
        // copy /bin/ files
        copyFileFromDumpToHmod("usr/bin/clover-factory-reset", "bin/clover-factory-reset-nes");
        copyFileFromDumpToHmod("usr/bin/clover-kachikachi", "bin/clover-kachikachi");
        copyFileFromDumpToHmod("usr/bin/clover-mcp", "bin/clover-mcp-nes");
        copyFileFromDumpToHmod("usr/bin/clover-menu-reset", "bin/clover-menu-reset-nes");
        copyFileFromDumpToHmod("usr/bin/clover-production-test-menu", "bin/clover-production-test-menu-nes");
        copyFileFromDumpToHmod("usr/bin/clover-ui", "bin/clover-ui-nes");
        copyFileFromDumpToHmod("usr/bin/kachikachi", "bin/kachikachi");
        copyFileFromDumpToHmod("usr/bin/ReedPlayer-Clover", "bin/ReedPlayer-Clover-nes");

        // copy /usr/share/ files
        copyDirectoryFromDumpToHmod("usr/share/applications", "etc/share/applications");
        copyDirectoryFromDumpToHmod("usr/share/clover-mcp", "etc/share/clover-mcp");
        copyDirectoryFromDumpToHmod("usr/share/clover-ui", "etc/share/clover-ui");
        copyDirectoryFromDumpToHmod("usr/share/kachikachi", "etc/share/kachikachi");
        copyDirectoryFromDumpToHmod("usr/share/legal", "etc/share/legal");
        copyDirectoryFromDumpToHmod("usr/share/locale", "etc/share/locale");
        copyDirectoryFromDumpToHmod("usr/share/reed-libs", "etc/share/reed-libs");

        // copy /lib files
        copyFileFromDumpToHmod("usr/lib/liblzo2.so.2.0.0", "lib/liblzo2.so");
        copyFileFromDumpToHmod("usr/lib/liblzo2.so.2.0.0", "lib/liblzo2.so.2");
        copyFileFromDumpToHmod("usr/lib/liblzo2.so.2.0.0", "lib/liblzo2.so.2.0.0");

        // copy /usr/share/games/nes/kachikachi
        copyDirectoryFromDumpToHmod("usr/share/games/nes/kachikachi", "etc/nesgames");
    }

    /**
     * Copy the given file from the source (NESC dump) to the output (HMOD) folder.
     * @param sourceFile The path of the source file within the NESC dump
     * @param targetFile The path within the HMOD to copy the file to
     * @throws IOException
     */
    private static void copyFileFromDumpToHmod(final String sourceFile, final String targetFile)
            throws IOException {
        final String src = String.format("%s/%s", DUMP_FOLDER, sourceFile);
        final String dst = String.format("%s/%s", HMOD_FOLDER, targetFile);
        System.out.println(String.format("Copying %s from the NESC dump to %s", src, dst));
        FileUtils.copyFile(new File(src), new File(dst));
    }

    /**
     * Copy the given directory from the source (NESC dump) to the output (HMOD) folder.
     * @param sourceDirectory The path of the source file within the NESC dump
     * @param targetDirectory The path within the HMOD to copy the file to
     * @throws IOException
     */
    private static void copyDirectoryFromDumpToHmod(final String sourceDirectory, final String targetDirectory)
            throws IOException {
        final String src = String.format("%s/%s", DUMP_FOLDER, sourceDirectory);
        final String dst = String.format("%s/%s", HMOD_FOLDER, targetDirectory);
        System.out.println(String.format("Copying %s from the NESC dump to %s", src, dst));
        FileUtils.copyDirectory(new File(src), new File(dst));
    }

    /**
     * Patch the non-game text scripts in the HMOD.
     * @throws IOException
     */
    private static void patchTextScriptsInHmod()
            throws IOException {
        // patch the desktop files
        replaceStringInFile("etc/share/applications/clover-debug-menu.desktop",
                "Exec=/usr/bin/clover-debug-menu",
                "Exec=/bin/clover-debug-menu-nes");
        replaceStringInFile("etc/share/applications/clover-factory-reset.desktop",
                "Exec=/usr/bin/clover-factory-reset",
                "Exec=/bin/clover-factory-reset-nes");
        replaceStringInFile("etc/share/applications/clover-menu-reset.desktop",
                "Exec=/usr/bin/clover-menu-reset",
                "Exec=/bin/clover-menu-reset-nes");
        replaceStringInFile("etc/share/applications/clover-test-menu.desktop",
                "Exec=/usr/bin/clover-production-test-menu",
                "Exec=/bin/clover-production-test-menu-nes");
        replaceStringInFile("etc/share/applications/clover-ui.desktop",
                "Exec=/usr/bin/clover-ui",
                "Exec=/bin/clover-ui-nes");
        replaceStringsInFile("etc/share/applications/clover-mcp.desktop", ImmutableMap.of(
                "/usr/share/games/nes/kachikachi",
                "/etc/nesgames",
                "/usr/share/applications",
                "/etc/share/applications",
                "/usr/share/clover-mcp/",
                "/etc/share/clover-mcp/"
                ));

        // patch the bin scripts
        replaceStringInFile("bin/clover-menu-reset-nes",
                "home-menu",
                "nesc-menu");
        replaceStringsInFile("bin/clover-ui-nes", ImmutableMap.of(
                "ReedPlayer-Clover",
                "ReedPlayer-Clover-nes",
                "/usr/share/",
                "/etc/share/"));
    }

    /**
     * Patches up the NES game desktop files to load correctly.
     * @throws IOException
     */
    private static void patchGameFilesInHmod()
            throws IOException {
        String[] gamesList = new String[]{};

        if ((chosenDumpFile == NES_102_DUMP) ||
                (chosenDumpFile == NES_103_DUMP)) {
            // this is a USA/EUR dump
            gamesList = new String[] { "CLV-P-NAAAE", "CLV-P-NAACE", "CLV-P-NAADE", "CLV-P-NAAEE", "CLV-P-NAAFE",
                    "CLV-P-NAAHE", "CLV-P-NAANE", "CLV-P-NAAPE", "CLV-P-NAAQE", "CLV-P-NAARE", "CLV-P-NAASE",
                    "CLV-P-NAATE", "CLV-P-NAAUE", "CLV-P-NAAVE", "CLV-P-NAAWE", "CLV-P-NAAXE", "CLV-P-NAAZE",
                    "CLV-P-NABBE", "CLV-P-NABCE", "CLV-P-NABJE", "CLV-P-NABKE", "CLV-P-NABME", "CLV-P-NABNE",
                    "CLV-P-NABQE", "CLV-P-NABRE", "CLV-P-NABVE", "CLV-P-NABXE", "CLV-P-NACBE", "CLV-P-NACDE",
                    "CLV-P-NACHE", "PRODUCTION-TESTS" };
        } else if (chosenDumpFile == HVC_105_DUMP) {
            // this is a JPN dump
            gamesList = new String[] { "CLV-P-HAAAJ", "CLV-P-HAACJ", "CLV-P-HAADJ", "CLV-P-HAAEJ", "CLV-P-HAAHJ",
                    "CLV-P-HAAMJ", "CLV-P-HAANJ", "CLV-P-HAAPJ", "CLV-P-HAAQJ", "CLV-P-HAARJ", "CLV-P-HAASJ",
                    "CLV-P-HAAUJ", "CLV-P-HAAWJ", "CLV-P-HAAXJ", "CLV-P-HABBJ", "CLV-P-HABCJ", "CLV-P-HABLJ",
                    "CLV-P-HABMJ", "CLV-P-HABNJ", "CLV-P-HABQJ", "CLV-P-HABRJ", "CLV-P-HABVJ", "CLV-P-HACAJ",
                    "CLV-P-HACBJ", "CLV-P-HACCJ", "CLV-P-HACEJ", "CLV-P-HACHJ", "CLV-P-HACJJ", "CLV-P-HACLJ",
                    "CLV-P-HACPJ", "PRODUCTION-TESTS" };
        }

        for (String gameCode : gamesList) {
            replaceStringsInFile(String.format("etc/nesgames/%1$s/%1$s.desktop", gameCode), ImmutableMap.of(
                    "/usr/bin/clover-kachikachi",
                    "/bin/clover-kachikachi-wr",
                    "/usr/share/games/nes/kachikachi",
                    "/etc/nesgames"
            ));
        }
    }

    /**
     * Replace all occurrences of a given string with a new string in the given file.
     * @param filePath The path of the file to patch within the HMOD
     * @param origStr The string to replace
     * @param newStr The replacement string
     * @throws IOException
     */
    private static void replaceStringInFile(final String filePath, final String origStr, final String newStr)
            throws IOException {
        final Path path = Paths.get(String.format("%s/%s", HMOD_FOLDER, filePath));
        System.out.println(String.format("Patching %s...", filePath));
        String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        content = content.replaceAll(origStr, newStr);
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Repalce all occurrences of the strings in a map with their given substitutions in the given file.
     * @param filePath The path of the file to patch within the HMOD
     * @param substitutions The strings to replace and their replacements
     * @throws IOException
     */
    private static void replaceStringsInFile(final String filePath, final Map<String, String> substitutions)
            throws IOException {
        final Path path = Paths.get(String.format("%s/%s", HMOD_FOLDER, filePath));
        System.out.println(String.format("Patching %s...", filePath));
        String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);

        for (Map.Entry<String, String> entry : substitutions.entrySet()) {
            content = content.replaceAll(entry.getKey(), entry.getValue());
        }

        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Patch up the binary files in the HMOD.
     */
    private static void patchBinaryFilesInHmod() throws IOException {
        // determine file offsets based on the version of the dump used
        long cloverMcpOffset = 0x0L;
        long kachikachiOffset1 = 0x0L;
        long kachikachiOffset2 = 0x0L;
        long reedplayerOffset1 = 0x0L;
        long reedplayerOffset2 = 0x0L;

        if ((chosenDumpFile == NES_102_DUMP) ||
                (chosenDumpFile == NES_103_DUMP)) {
            cloverMcpOffset = 0x209C5;
            kachikachiOffset1 = 0x5D00D;
            kachikachiOffset2 = 0x5D048;
            reedplayerOffset1 = 0x12C0B5;
            reedplayerOffset2 = 0x12C0FC;
        } else if (chosenDumpFile == HVC_105_DUMP) {
            cloverMcpOffset = 0x209C5;
            kachikachiOffset1 = 0x602BD;
            kachikachiOffset2 = 0x602F8;
            reedplayerOffset1 = 0x12C0B5;
            reedplayerOffset2 = 0x12C0FC;
        }

        // patch the files based on the determined offsets
        String path = String.format("%s/bin/clover-mcp-nes", HMOD_FOLDER);
        System.out.println(String.format("Patching %s...", path));
        RandomAccessFile file = new RandomAccessFile(new File(path), "rw");
        file.seek(cloverMcpOffset);
        file.writeByte(0x65);
        file.writeByte(0x74);
        file.writeByte(0x63);

        path = String.format("%s/bin/kachikachi", HMOD_FOLDER);
        System.out.println(String.format("Patching %s...", path));
        file = new RandomAccessFile(new File(path), "rw");
        file.seek(kachikachiOffset1);
        file.writeByte(0x65);
        file.writeByte(0x74);
        file.writeByte(0x63);
        file.seek(kachikachiOffset2);
        file.writeByte(0x65);
        file.writeByte(0x74);
        file.writeByte(0x63);

        path = String.format("%s/bin/ReedPlayer-Clover-nes", HMOD_FOLDER);
        System.out.println(String.format("Patching %s...", path));
        file = new RandomAccessFile(new File(path), "rw");
        file.seek(reedplayerOffset1);
        file.writeByte(0x65);
        file.writeByte(0x74);
        file.writeByte(0x63);
        file.seek(reedplayerOffset2);
        file.writeByte(0x6E);
        file.writeByte(0x65);
        file.writeByte(0x73);
        file.writeByte(0x63);
    }
}
