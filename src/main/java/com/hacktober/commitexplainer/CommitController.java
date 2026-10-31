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

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class CommitController {

    record PedidoLista(String caminho, String url) {}
    record PedidoExplicar(String caminho, String url, String hash) {}

    private final GitService git;
    private final GitHubService github;
    private final CommitService service;

    public CommitController(GitService git, GitHubService github, CommitService service) {
        this.git = git;
        this.github = github;
        this.service = service;
    }

    private boolean temUrl(String url) {
        return url != null && !url.isBlank();
    }

    @PostMapping("/commits")
    public ResponseEntity<?> commits(@RequestBody PedidoLista pedido) {
        try {
            List<GitService.Commit> lista = temUrl(pedido.url())
                    ? github.listar(pedido.url())
                    : git.listar(pedido.caminho());
            return ResponseEntity.ok(lista);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("erro",
                    "Não consegui listar os commits. Se for um caminho local, o Git está instalado e no PATH?"));
        }
    }

    @PostMapping("/explicar")
    public ResponseEntity<?> explicar(@RequestBody PedidoExplicar pedido) {
        try {
            var r = service.explicar(pedido.caminho(), pedido.url(), pedido.hash());
            return ResponseEntity.ok(Map.of(
                    "explicacao", r.explicacao(),
                    "diffCortado", r.diffCortado()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("erro", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(503).body(Map.of("erro",
                    "Não consegui falar com o Ollama. Ele está rodando?"));
        }
    }
}