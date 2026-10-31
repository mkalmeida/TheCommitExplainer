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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class GitHubService {

    private static final Pattern URL_REPO = Pattern.compile(
            "^https://github\\.com/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+?)(?:\\.git)?/?$");
    private static final Pattern HASH = Pattern.compile("^[0-9a-fA-F]{7,40}$");

    private final RestClient client;

    public GitHubService(@Value("${github.token:}") String token) {
        var factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(20));

        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.github.com")
                .requestFactory(factory)
                .defaultHeader("User-Agent", "commit-explainer")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28");
        if (token != null && !token.isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + token);
        }
        this.client = builder.build();
    }

    public List<GitService.Commit> listar(String url) {
        String[] repo = interpretar(url);
        try {
            List<Map<String, Object>> dados = client.get()
                    .uri("/repos/{dono}/{repo}/commits?per_page=20", repo[0], repo[1])
                    .header("Accept", "application/vnd.github+json")
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

            List<GitService.Commit> commits = new ArrayList<>();
            if (dados == null) return commits;

            for (Map<String, Object> item : dados) {
                Map<String, Object> commit = mapa(item.get("commit"));
                Map<String, Object> autor = mapa(commit.get("author"));

                String mensagem = String.valueOf(commit.getOrDefault("message", ""));
                int quebra = mensagem.indexOf('\n');
                if (quebra >= 0) mensagem = mensagem.substring(0, quebra);

                String data = String.valueOf(autor.getOrDefault("date", ""));
                if (data.length() > 10) data = data.substring(0, 10);

                commits.add(new GitService.Commit(
                        String.valueOf(item.get("sha")),
                        String.valueOf(autor.getOrDefault("name", "desconhecido")),
                        data,
                        mensagem));
            }
            return commits;
        } catch (RestClientResponseException e) {
            throw traduzir(e);
        } catch (ResourceAccessException e) {
            throw new IllegalArgumentException("Não consegui acessar o GitHub. Confira a internet.");
        }
    }

    public String mostrar(String url, String hash) {
        String[] repo = interpretar(url);
        if (hash == null || !HASH.matcher(hash).matches()) {
            throw new IllegalArgumentException("Identificador de commit inválido.");
        }
        try {
            return client.get()
                    .uri("/repos/{dono}/{repo}/commits/{hash}", repo[0], repo[1], hash)
                    .header("Accept", "application/vnd.github.v3.patch")
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            throw traduzir(e);
        } catch (ResourceAccessException e) {
            throw new IllegalArgumentException("Não consegui acessar o GitHub. Confira a internet.");
        }
    }

    private String[] interpretar(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Informe a URL do repositório.");
        }
        Matcher m = URL_REPO.matcher(url.trim());
        if (!m.matches()) {
            throw new IllegalArgumentException(
                    "URL inválida. Use o formato https://github.com/usuario/repositorio");
        }
        return new String[]{m.group(1), m.group(2)};
    }

    private RuntimeException traduzir(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        if (status == 404) {
            return new IllegalArgumentException("Repositório ou commit não encontrado (ou o repositório é privado).");
        }
        if (status == 403 || status == 429) {
            return new IllegalArgumentException(
                    "Limite de requisições da API do GitHub atingido. Tente mais tarde ou configure um GITHUB_TOKEN.");
        }
        return new IllegalArgumentException("O GitHub respondeu com erro " + status + ".");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapa(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : Map.of();
    }
}
