package com.hitorro.gittools.pr;

import java.util.List;

public record BatchPrJob(
        List<String> commitHashes,
        String baseBranch,
        String branchNameTemplate,
        String titleTemplate,
        String bodyTemplate
) {}
