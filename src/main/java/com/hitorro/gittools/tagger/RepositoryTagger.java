package com.hitorro.gittools.tagger;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.hitorro.gittools.model.RepositoryMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages custom tags and descriptions for repositories.
 * Persists to a JSON file keyed by canonical repository path.
 */
public class RepositoryTagger {

    private static final Logger log = LoggerFactory.getLogger(RepositoryTagger.class);
    private final Path metadataFile;
    private final ObjectMapper mapper;
    private Map<String, RepositoryMetadata> metadataMap;

    public RepositoryTagger(Path metadataFile) {
        this.metadataFile = metadataFile;
        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        this.metadataMap = new ConcurrentHashMap<>();
        load();
    }

    public void load() {
        if (metadataFile != null && Files.exists(metadataFile)) {
            try {
                metadataMap = new ConcurrentHashMap<>(mapper.readValue(
                        metadataFile.toFile(),
                        new TypeReference<Map<String, RepositoryMetadata>>() {}));
                log.info("Loaded metadata for {} repositories from {}", metadataMap.size(), metadataFile);
            } catch (IOException e) {
                log.error("Failed to load metadata from {}: {}", metadataFile, e.getMessage());
                metadataMap = new ConcurrentHashMap<>();
            }
        }
    }

    public void save() {
        if (metadataFile == null) return;
        try {
            Files.createDirectories(metadataFile.getParent());
            mapper.writeValue(metadataFile.toFile(), metadataMap);
        } catch (IOException e) {
            log.error("Failed to save metadata to {}: {}", metadataFile, e.getMessage());
        }
    }

    public RepositoryMetadata getMetadata(Path repoPath) {
        String key = normalize(repoPath);
        return metadataMap.computeIfAbsent(key, k -> new RepositoryMetadata());
    }

    public void setDescription(Path repoPath, String description) {
        getMetadata(repoPath).setDescription(description);
        save();
    }

    public void addTag(Path repoPath, String tag) {
        var meta = getMetadata(repoPath);
        if (!meta.getTags().contains(tag)) {
            meta.getTags().add(tag);
            save();
        }
    }

    public void removeTag(Path repoPath, String tag) {
        getMetadata(repoPath).getTags().remove(tag);
        save();
    }

    public void setTags(Path repoPath, List<String> tags) {
        getMetadata(repoPath).setTags(new ArrayList<>(tags));
        save();
    }

    public void setLanguage(Path repoPath, String language) {
        getMetadata(repoPath).setLanguage(language);
        save();
    }

    public List<String> findByTag(String tag) {
        List<String> repos = new ArrayList<>();
        for (var entry : metadataMap.entrySet()) {
            if (entry.getValue().getTags().contains(tag)) {
                repos.add(entry.getKey());
            }
        }
        return repos;
    }

    public List<String> getAllTags() {
        Set<String> tags = new TreeSet<>();
        metadataMap.values().forEach(m -> tags.addAll(m.getTags()));
        return new ArrayList<>(tags);
    }

    private String normalize(Path path) {
        try { return path.toRealPath().toString(); }
        catch (Exception e) { return path.toAbsolutePath().toString(); }
    }
}
