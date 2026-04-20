package com.hitorro.gittools.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom metadata for a repository, persisted to a JSON file.
 */
public class RepositoryMetadata {
    private String description;
    private List<String> tags;
    private String language;
    private String notes;

    public RepositoryMetadata() {
        this.tags = new ArrayList<>();
    }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags != null ? tags : new ArrayList<>(); }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
