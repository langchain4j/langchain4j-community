package dev.langchain4j.community.rag.content.retriever.lucene;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MMapDirectory;

/**
 * Static factory for create Lucene directories.
 */
public class DirectoryFactory {

    /**
     * Create a memory mapped file-system based directory.
     *
     * @param directoryPath Path for the directory.
     * @return Lucene directory
     */
    public static Directory fsDirectory(Path directoryPath) {
        ensureNotNull(directoryPath, "directoryPath");
        try {
            Directory directory = new MMapDirectory(directoryPath);
            return directory;
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    /**
     * Create a memory mapped file-system based directory, in a temporary directory.
     *
     * @return Lucene directory
     */
    public static Directory tempDirectory() {
        try {
            Path directoryPath = Files.createTempDirectory(Directory.class.getCanonicalName());
            Path newSubDirectory = Paths.get(directoryPath.toString(), Directory.class.getCanonicalName());
            return fsDirectory(newSubDirectory);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private DirectoryFactory() {
        // Prevent instantiation
    }
}
