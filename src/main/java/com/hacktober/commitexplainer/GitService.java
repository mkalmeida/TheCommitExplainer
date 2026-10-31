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

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Component
public class GitService {

    public record Commit(String hash, String autor, String data, String mensagem) {}

    private static final Pattern HASH = Pattern.compile("^[0-9a-fA-F]{7,40}$");
    private static final int MAX_BYTES = 200_000;
    private static final int TIMEOUT_SEGUNDOS = 15;

    public List<Commit> listar(String caminho) throws IOException, InterruptedException {
        Path repo = validarRepositorio(caminho);
        String saida = executar(repo, "log", "-n", "20", "--date=short",
                "--pretty=format:%H%x1f%an%x1f%ad%x1f%s");

        List<Commit> commits = new ArrayList<>();
        for (String linha : saida.split("\\R")) {
            String[] p = linha.split("\u001f", -1);
            if (p.length == 4) {
                commits.add(new Commit(p[0], p[1], p[2], p[3]));
            }
        }
        return commits;
    }

    public String mostrar(String caminho, String hash) throws IOException, InterruptedException {
        Path repo = validarRepositorio(caminho);
        if (hash == null || !HASH.matcher(hash).matches()) {
            throw new IllegalArgumentException("Identificador de commit inválido.");
        }
        return executar(repo, "show", "--no-color", "--no-ext-diff", "--no-textconv",
                "--stat", "--patch", hash, "--");
    }

    private Path validarRepositorio(String caminho) throws IOException, InterruptedException {
        if (caminho == null || caminho.isBlank()) {
            throw new IllegalArgumentException("Informe o caminho do repositório.");
        }
        Path pasta;
        try {
            pasta = Path.of(caminho.trim()).toAbsolutePath().normalize();
        } catch (Exception e) {
            throw new IllegalArgumentException("Caminho inválido.");
        }
        if (!Files.isDirectory(pasta)) {
            throw new IllegalArgumentException("Pasta não encontrada.");
        }
        String dentro = executar(pasta, "rev-parse", "--is-inside-work-tree").trim();
        if (!"true".equals(dentro)) {
            throw new IllegalArgumentException("Essa pasta não é um repositório Git.");
        }
        return pasta;
    }

    private String executar(Path repo, String... args) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>(List.of("git", "-C", repo.toString(), "--no-pager",
                "-c", "core.quotepath=off", "-c", "i18n.logOutputEncoding=UTF-8"));
        cmd.addAll(List.of(args));

        Process processo = new ProcessBuilder(cmd).redirectErrorStream(true).start();

        byte[] bytes;
        try (InputStream in = processo.getInputStream()) {
            bytes = in.readNBytes(MAX_BYTES);
        }
        String texto = new String(bytes, StandardCharsets.UTF_8);

        if (bytes.length == MAX_BYTES) {          // saída enorme: já temos o suficiente
            processo.destroyForcibly();
            return texto;
        }
        if (!processo.waitFor(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS)) {
            processo.destroyForcibly();
            throw new IOException("O Git demorou demais para responder.");
        }
        if (processo.exitValue() != 0) {
            throw new IllegalArgumentException("O Git não conseguiu executar isso. Confira o caminho e o commit.");
        }
        return texto;
    }
}
