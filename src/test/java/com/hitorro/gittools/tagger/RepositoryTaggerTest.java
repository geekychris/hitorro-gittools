package com.hitorro.gittools.tagger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Repository Tagger")
class RepositoryTaggerTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldSetAndGetDescription() {
        var tagger = new RepositoryTagger(tempDir.resolve("meta.json"));
        Path repo = Path.of("/test/repo");
        tagger.setDescription(repo, "My project");
        assertThat(tagger.getMetadata(repo).getDescription()).isEqualTo("My project");
    }

    @Test
    void shouldManageTags() {
        var tagger = new RepositoryTagger(tempDir.resolve("meta.json"));
        Path repo = Path.of("/test/repo");
        tagger.addTag(repo, "java");
        tagger.addTag(repo, "search");
        assertThat(tagger.getMetadata(repo).getTags()).containsExactly("java", "search");

        tagger.removeTag(repo, "java");
        assertThat(tagger.getMetadata(repo).getTags()).containsExactly("search");
    }

    @Test
    void shouldPersistAndReload() {
        Path metaFile = tempDir.resolve("meta.json");
        var tagger1 = new RepositoryTagger(metaFile);
        tagger1.setDescription(Path.of("/test/repo"), "Description");
        tagger1.addTag(Path.of("/test/repo"), "ml");
        tagger1.save();

        var tagger2 = new RepositoryTagger(metaFile);
        assertThat(tagger2.getMetadata(Path.of("/test/repo")).getDescription()).isEqualTo("Description");
        assertThat(tagger2.getMetadata(Path.of("/test/repo")).getTags()).contains("ml");
    }

    @Test
    void shouldFindByTag() {
        var tagger = new RepositoryTagger(tempDir.resolve("meta.json"));
        tagger.addTag(Path.of("/repo1"), "java");
        tagger.addTag(Path.of("/repo2"), "java");
        tagger.addTag(Path.of("/repo3"), "python");
        assertThat(tagger.findByTag("java")).hasSize(2);
    }

    @Test
    void shouldListAllTags() {
        var tagger = new RepositoryTagger(tempDir.resolve("meta.json"));
        tagger.addTag(Path.of("/repo1"), "java");
        tagger.addTag(Path.of("/repo2"), "search");
        assertThat(tagger.getAllTags()).contains("java", "search");
    }
}
