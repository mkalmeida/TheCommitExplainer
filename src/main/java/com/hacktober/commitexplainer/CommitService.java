/*
 * Copyright (C) 2026 [Seu Nome ou Seu Usuário do GitHub]
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 */

package com.hacktober.commitexplainer;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class CommitService {

    public record Resultado(String explicacao, boolean diffCortado) {}

    private static final int MAX_CARACTERES = 12000;

    private static final Pattern SEGREDO = Pattern.compile(
            "(?i)(password|senha|token|secret|apikey|api_key)\\s*[=:]\\s*\\S+");

    private static final List<String> IGNORED_FILES = List.of(
            "mvnw", "mvnw.cmd", "package-lock.json", "yarn.lock", "composer.lock"
    );

    private final GitService git;
    private final GitHubService github;
    private final OllamaClient ollama;

    public CommitService(GitService git, GitHubService github, OllamaClient ollama) {
        this.git = git;
        this.github = github;
        this.ollama = ollama;
    }

    public Resultado explicar(String caminho, String url, String hash)
            throws IOException, InterruptedException {

        String diff = (url != null && !url.isBlank())
                ? github.mostrar(url, hash)
                : git.mostrar(caminho, hash);

        // 1. Hide secrets
        diff = SEGREDO.matcher(diff).replaceAll("$1=***");

        // 2. Filter out noisy/unimportant diff blocks
        diff = filterIrrelevantDiffs(diff);

        // 3. Max length check
        boolean cortado = diff.length() > MAX_CARACTERES;
        if (cortado) {
            diff = diff.substring(0, MAX_CARACTERES);
        }

        String modelo = new ClassPathResource("prompts/commit.txt")
                .getContentAsString(StandardCharsets.UTF_8);

        String aviso = cortado
                ? "WARNING: the diff below was truncated because it was too large. Mention this in your response."
                : "";

        String prompt = modelo
                .replace("{{AVISO}}", aviso)
                .replace("{{DIFF}}", diff);

        return new Resultado(ollama.gerar(prompt), cortado);
    }

    private String filterIrrelevantDiffs(String diffOriginal) {
        String[] files = diffOriginal.split("(?=diff --git )");
        StringBuilder sb = new StringBuilder();

        for (String block : files) {
            boolean ignore = false;
            for (String ignoredFile : IGNORED_FILES) {
                if (block.contains("a/" + ignoredFile) || block.contains("/.mvn/")) {
                    ignore = true;
                    break;
                }
            }
            if (!ignore) {
                sb.append(block);
            } else {
                sb.append("[Diff omitted for configuration/wrapper file]\n");
            }
        }
        return sb.toString();
    }
}